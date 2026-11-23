package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.ai.GeneratedClipCandidate
import com.example.data.model.AnalysisStatus
import com.example.data.model.ClipCategory
import com.example.data.model.HighlightClip
import com.example.data.model.StreamAnalysis
import com.example.data.model.StreamSourceType
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ClipPulseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeStreams(userId: String): Flow<List<StreamAnalysis>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user stream read." }
        val path = "users/$uid/streams"
        emitAll(
            db.collection("users").document(uid).collection("streams")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StreamAnalysis::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeClips(userId: String): Flow<List<HighlightClip>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user clip read." }
        val path = "users/$uid/clips"
        emitAll(
            db.collection("users").document(uid).collection("clips")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        HighlightClip::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createStreamScan(
        title: String,
        channelName: String,
        sourceType: StreamSourceType,
        sourceRef: String,
        durationSeconds: Int
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/streams"
        return try {
            val uid = requireUserId()
            val safeTitle = title.trim().take(120).ifBlank { "Twitch Stream Scan" }
            val safeChannel = channelName.trim().take(60)
            val safeRef = sourceRef.trim().take(500)
            val safeDuration = durationSeconds.coerceIn(0, 86400)

            val docRef = db.collection("users").document(uid).collection("streams").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "channelName" to safeChannel,
                "sourceType" to sourceType.name,
                "sourceRef" to safeRef,
                "durationSeconds" to safeDuration,
                "clipCount" to 0,
                "peakHypeScore" to 0,
                "summary" to "AI scanning stream for peak highlight moments...",
                "status" to AnalysisStatus.ANALYZING.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun completeStreamScanWithClips(
        streamId: String,
        streamTitle: String,
        channelName: String,
        durationSeconds: Int,
        summary: String,
        localVideoUri: String?,
        candidates: List<GeneratedClipCandidate>
    ): Result<Int> {
        var opPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/streams/$streamId"
        return try {
            val uid = requireUserId()
            require(streamId.isNotBlank()) { "Stream ID must not be blank" }

            val safeTitle = streamTitle.trim().take(120).ifBlank { "Twitch Stream" }
            val safeChannel = channelName.trim().take(60)
            val safeDuration = durationSeconds.coerceIn(1, 86400)
            val safeSummary = summary.trim().take(1000)
            val safeLocalUri = localVideoUri?.trim()?.take(500)?.takeIf { it.isNotEmpty() }

            val streamRef = db.collection("users").document(uid).collection("streams").document(streamId)
            opPath = streamRef.path

            val peakHype = candidates.maxOfOrNull { it.hypeScore }?.coerceIn(0, 100) ?: 0
            val batch = db.batch()

            batch.update(
                streamRef,
                mapOf(
                    "status" to AnalysisStatus.READY.name,
                    "title" to safeTitle,
                    "durationSeconds" to safeDuration,
                    "clipCount" to candidates.size.coerceIn(0, 500),
                    "peakHypeScore" to peakHype,
                    "summary" to safeSummary,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val clipsCollection = db.collection("users").document(uid).collection("clips")
            candidates.forEach { clip ->
                val clipRef = clipsCollection.document()
                val safeStart = clip.startSec.coerceIn(0, 86399)
                val safeEnd = clip.endSec.coerceIn(safeStart + 1, 86400)
                val rawMap = mapOf(
                    "userId" to uid,
                    "streamId" to streamId,
                    "streamTitle" to safeTitle,
                    "channelName" to safeChannel,
                    "title" to clip.title.trim().take(120).ifBlank { "AI Highlight" },
                    "hookCaption" to clip.hookCaption.trim().take(180),
                    "engagementReason" to clip.engagementReason.trim().take(500),
                    "category" to clip.category.name,
                    "startSec" to safeStart,
                    "endSec" to safeEnd,
                    "hypeScore" to clip.hypeScore.coerceIn(0, 100),
                    "starred" to false,
                    "exported" to false,
                    "localVideoUri" to safeLocalUri,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }

                batch.set(clipRef, rawMap)
            }

            batch.commit().await()
            Result.success(candidates.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, opPath)
            Result.failure(e)
        }
    }

    suspend fun failStreamScan(streamId: String, reason: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/streams/$streamId"
        return try {
            val uid = requireUserId()
            require(streamId.isNotBlank()) { "Stream ID must not be blank" }
            val streamRef = db.collection("users").document(uid).collection("streams").document(streamId)
            docPath = streamRef.path
            streamRef.update(
                mapOf(
                    "status" to AnalysisStatus.FAILED.name,
                    "summary" to reason.trim().take(1000).ifBlank { "Stream scan failed." },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleClipStarred(clipId: String, starred: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/clips/$clipId"
        return try {
            val uid = requireUserId()
            require(clipId.isNotBlank()) { "Clip ID must not be blank" }
            val clipRef = db.collection("users").document(uid).collection("clips").document(clipId)
            docPath = clipRef.path
            clipRef.update(
                mapOf(
                    "starred" to starred,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markClipExported(clipId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/clips/$clipId"
        return try {
            val uid = requireUserId()
            require(clipId.isNotBlank()) { "Clip ID must not be blank" }
            val clipRef = db.collection("users").document(uid).collection("clips").document(clipId)
            docPath = clipRef.path
            clipRef.update(
                mapOf(
                    "exported" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateClipDetails(
        clipId: String,
        title: String,
        hookCaption: String,
        category: ClipCategory,
        startSec: Int,
        endSec: Int
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/clips/$clipId"
        return try {
            val uid = requireUserId()
            require(clipId.isNotBlank()) { "Clip ID must not be blank" }
            val safeTitle = title.trim().take(120)
            require(safeTitle.isNotEmpty()) { "Clip title cannot be empty." }
            val safeStart = startSec.coerceIn(0, 86399)
            val safeEnd = endSec.coerceIn(safeStart + 1, 86400)

            val clipRef = db.collection("users").document(uid).collection("clips").document(clipId)
            docPath = clipRef.path
            clipRef.update(
                mapOf(
                    "title" to safeTitle,
                    "hookCaption" to hookCaption.trim().take(180),
                    "category" to category.name,
                    "startSec" to safeStart,
                    "endSec" to safeEnd,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteClip(clipId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/clips/$clipId"
        return try {
            val uid = requireUserId()
            require(clipId.isNotBlank()) { "Clip ID must not be blank" }
            val clipRef = db.collection("users").document(uid).collection("clips").document(clipId)
            docPath = clipRef.path
            clipRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteStreamWithClips(streamId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/streams/$streamId"
        return try {
            val uid = requireUserId()
            require(streamId.isNotBlank()) { "Stream ID must not be blank" }
            val streamRef = db.collection("users").document(uid).collection("streams").document(streamId)
            docPath = streamRef.path

            val childClips = db.collection("users").document(uid).collection("clips")
                .whereEqualTo("streamId", streamId)
                .get()
                .await()

            val batch = db.batch()
            childClips.documents.forEach { batch.delete(it.reference) }
            batch.delete(streamRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
