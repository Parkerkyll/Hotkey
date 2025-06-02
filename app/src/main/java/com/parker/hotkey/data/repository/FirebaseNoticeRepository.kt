package com.parker.hotkey.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.parker.hotkey.data.model.Notice
import com.parker.hotkey.domain.repository.NoticeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseNoticeRepository @Inject constructor() : NoticeRepository {
    private val database: FirebaseDatabase
    private val noticesRef: DatabaseReference

    init {
        Timber.d("FirebaseNoticeRepository 초기화")
        database = FirebaseDatabase.getInstance("https://hotkey-20143-default-rtdb.asia-southeast1.firebasedatabase.app")
        noticesRef = database.getReference("notices")
        Timber.d("Firebase 데이터베이스 참조 URL: ${database.reference}")
    }
    
    override suspend fun getNotices(): Result<List<Notice>> {
        return try {
            Timber.d("공지사항 조회 시작: ${noticesRef}")
            
            val snapshot = withContext(Dispatchers.IO) {
                noticesRef.orderByChild("createdAt").get().await()
            }
            
            Timber.d("Firebase 스냅샷: ${snapshot.exists()}, 자식 수: ${snapshot.childrenCount}")
            if (!snapshot.exists()) {
                Timber.w("스냅샷이 존재하지 않음: ${noticesRef}")
                return Result.success(emptyList())
            }
            
            val notices = snapshot.children.mapNotNull { child ->
                Timber.d("공지사항 항목: ${child.key}")
                try {
                    child.getValue(Notice::class.java)
                } catch (e: Exception) {
                    Timber.e(e, "공지사항 변환 실패: ${child.key}")
                    null
                }
            }.sortedByDescending { notice -> 
                notice.createdAt 
            }
            
            Timber.d("공지사항 조회 결과: ${notices.size}개")
            Result.success(notices)
        } catch (e: Exception) {
            Timber.e(e, "공지사항 목록 조회 실패")
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
    
    override suspend fun getNoticeById(id: String): Result<Notice> {
        return try {
            Timber.d("공지사항 상세 조회 시작: $id")
            val snapshot = withContext(Dispatchers.IO) {
                noticesRef.child(id).get().await()
            }
            
            val notice = snapshot.getValue(Notice::class.java)
            
            if (notice != null) {
                Timber.d("공지사항 상세 조회 성공: $id")
                Result.success(notice)
            } else {
                Timber.w("공지사항 상세 조회 실패: 데이터 없음: $id")
                Result.failure(Exception("공지사항을 찾을 수 없습니다"))
            }
        } catch (e: Exception) {
            Timber.e(e, "공지사항 상세 조회 실패: $id")
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
} 