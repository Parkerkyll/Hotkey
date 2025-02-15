# AWS Lambda 함수 문서

기본 URL: https://rcnhm17c9b.execute-api.ap-northeast-2.amazonaws.com/prod

## API 엔드포인트 

- 마커 관련:
  - GET /markers
    ARN: arn:aws:execute-api:ap-northeast-2:536697232564:rcnhm17c9b/*/GET/markers
  - POST /markers
    ARN: arn:aws:execute-api:ap-northeast-2:536697232564:rcnhm17c9b/*/POST/markers
  - DELETE /markers/{id}
    ARN: arn:aws:execute-api:ap-northeast-2:536697232564:rcnhm17c9b/*/DELETE/markers/{id}
  - GET /markers/{id}
    ARN: arn:aws:execute-api:ap-northeast-2:536697232564:rcnhm17c9b/*/GET/markers/{id}

- 메모 관련:
  - GET /markers/{id}/memos
    ARN: arn:aws:execute-api:ap-northeast-2:536697232564:rcnhm17c9b/*/GET/markers/{id}/memos
  - POST /markers/{id}/memos
    ARN: arn:aws:execute-api:ap-northeast-2:536697232564:rcnhm17c9b/*/POST/markers/{id}/memos
  - DELETE /markers/{id}/memos/{memoId}
    ARN: arn:aws:execute-api:ap-northeast-2:536697232564:rcnhm17c9b/*/DELETE/markers/{id}/memos/{memoId}
  - PUT /markers/{id}/memos/{memoId}
    ARN: arn:aws:execute-api:ap-northeast-2:536697232564:rcnhm17c9b/*/PUT/markers/{id}/memos/{memoId}


### 마커 관련
- GET /markers
  - 기능: 주변 마커 조회 (geohash 기반)
  - 인증: 필수 (JWT)
  - 파라미터: 
    - geohash: String (6자리)
    - lastSyncTime: Long (선택)
  - 응답:
    ```json
    {
      "markers": [
        {
          "id": "UUID",
          "geohash": "String",
          "title": "String",
          "description": "String",
          "latitude": "Number",
          "longitude": "Number",
          "createdAt": "Number",
          "updatedAt": "Number"
        }
      ],
      "deletedMarkerIds": ["UUID"],
      "syncTime": "Number"
    }
    ```

- POST /markers
  - 기능: 마커 생성
  - 인증: 필수 (JWT)
  - 요청:
    ```json
    {
      "title": "String",
      "description": "String",
      "latitude": "Number",
      "longitude": "Number"
    }
    ```

- DELETE /markers/{id}
  - 기능: 마커 삭제
  - 인증: 필수 (JWT)
  - 응답: HTTP 204

- GET /markers/{id}
  - 기능: 마커 상세 조회
  - 인증: 필수 (JWT)
  - 응답: Marker 객체

### 메모 관련
- GET /markers/{id}/memos
  - 기능: 메모 목록 조회
  - 인증: 필수 (JWT)
  - 응답: Memo 객체 배열

- POST /markers/{id}/memos
  - 기능: 메모 생성
  - 인증: 필수 (JWT)
  - 요청:
    ```json
    {
      "content": "String"
    }
    ```

- DELETE /markers/{id}/memos/{memoId}
  - 기능: 메모 삭제
  - 인증: 필수 (JWT)
  - 응답: HTTP 204

- PUT /markers/{id}/memos/{memoId}
  - 기능: 메모 수정
  - 인증: 필수 (JWT)
  - 요청:
    ```json
    {
      "content": "String"
    }
    ```

## Lambda 권한 설정
- 실행 역할: AWSLambdaBasicExecutionRole
- 추가 정책:
  - DynamoDB 접근 권한
  - CloudWatch Logs 권한
  - API Gateway 호출 권한

## 주요 함수 구현

### 마커 조회 함수 (GET /markers)
```python
def get_markers(event, context):
    try:
        # 파라미터 추출
        geohash = event['queryStringParameters']['geohash']
        last_sync = event['queryStringParameters'].get('lastSync', 0)
        
        # DynamoDB 쿼리
        markers = dynamodb.query(
            TableName='Markers',
            IndexName='geohash-index',
            KeyConditionExpression='geohash = :gh',
            FilterExpression='updatedAt > :ts',
            ExpressionAttributeValues={
                ':gh': {'S': geohash},
                ':ts': {'N': str(last_sync)}
            }
        )
        
        # 삭제된 마커 조회
        deleted_markers = dynamodb.query(
            TableName='DeletedMarkers',
            KeyConditionExpression='geohash = :gh AND deletedAt > :ts',
            ExpressionAttributeValues={
                ':gh': {'S': geohash},
                ':ts': {'N': str(last_sync)}
            }
        )
        
        return {
            'statusCode': 200,
            'body': json.dumps({
                'markers': parse_markers(markers['Items']),
                'deletedMarkerIds': [item['id']['S'] for item in deleted_markers['Items']],
                'syncTime': int(time.time() * 1000)
            })
        }
    except Exception as e:
        return error_response(500, str(e))
```

### 마커 생성 함수 (POST /markers)
```python
def create_marker(event, context):
    user_id = event['requestContext']['authorizer']['userId']
    body = json.loads(event['body'])
    
    marker = marker_service.create(
        user_id=user_id,
        **body
    )
    
    return {
        'id': marker.id,
        'version': marker.version,
        'createdAt': marker.created_at
    }
```

### 메모 생성 함수 (POST /markers/{id}/memos)
```python
def create_memo(event, context):
    try:
        # 파라미터 추출
        marker_id = event['pathParameters']['id']
        user_id = event['requestContext']['authorizer']['claims']['sub']
        body = json.loads(event['body'])
        
        # 마커 존재 확인
        marker = dynamodb.get_item(
            TableName='Markers',
            Key={'id': {'S': marker_id}}
        ).get('Item')
        
        if not marker:
            return error_response(404, 'Marker not found')
        
        # 메모 생성
        memo_id = str(uuid.uuid4())
        timestamp = int(time.time() * 1000)
        
        dynamodb.put_item(
            TableName='Memos',
            Item={
                'id': {'S': memo_id},
                'marker_id': {'S': marker_id},
                'content': {'S': body['content']},
                'user_id': {'S': user_id},
                'createdAt': {'N': str(timestamp)},
                'updatedAt': {'N': str(timestamp)}
            }
        )
        
        return {
            'statusCode': 201,
            'body': json.dumps({
                'id': memo_id,
                'content': body['content'],
                'createdAt': timestamp
            })
        }
    except Exception as e:
        return error_response(500, str(e))
```

## 에러 처리
```python
def error_response(status_code, message):
    return {
        'statusCode': status_code,
        'body': json.dumps({
            'error': {
                'code': status_code,
                'message': message
            }
        })
    }
```

## 성능 최적화
1. DynamoDB GSI 활용
   - geohash 기반 검색을 위한 GSI
   - marker_id 기반 메모 검색을 위한 GSI

2. 배치 처리
   - 대량의 마커/메모 동기화 시 BatchGetItem/BatchWriteItem 사용
   - 최대 100개 단위로 처리

3. 응답 압축
   - API Gateway에서 gzip 압축 활성화
   - 대용량 응답 시 네트워크 부하 감소

4. 캐시 전략
   - API Gateway 캐시 설정 (5분) -- 캐시 전략 사용안함 Room DB만 사용
   - 클라이언트 캐시 헤더 설정 -- 사용안함 Room DB만 사용