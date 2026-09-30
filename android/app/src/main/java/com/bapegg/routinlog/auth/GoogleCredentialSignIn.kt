package com.bapegg.routinlog.auth

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.bapegg.routinlog.data.AccountErrorKind
import com.bapegg.routinlog.data.AccountException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

class GoogleCredentialSignIn(private val webClientId: String) {
    suspend fun idToken(activity: Activity, nonce: String): String {
        if (webClientId.isBlank()) throw AccountException(AccountErrorKind.CONFIGURATION, "Google 로그인 설정이 아직 준비되지 않았어요.")
        try {
            val option = GetSignInWithGoogleOption.Builder(webClientId).setNonce(nonce).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val credential = CredentialManager.create(activity.applicationContext)
                .getCredential(MutableContextWrapper(activity), request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                throw AccountException(AccountErrorKind.CREDENTIAL, "Google 계정 정보를 확인하지 못했어요. 다시 시도해주세요.")
            }
            return GoogleIdTokenCredential.createFrom(credential.data).idToken.takeIf { it.isNotBlank() }
                ?: throw AccountException(AccountErrorKind.CREDENTIAL, "Google 계정 정보를 확인하지 못했어요.")
        } catch (error: GetCredentialCancellationException) {
            throw AccountException(AccountErrorKind.CANCELLED, "로그인을 취소했어요.")
        } catch (_: NoCredentialException) {
            throw AccountException(AccountErrorKind.CREDENTIAL, "사용 가능한 Google 계정을 찾지 못했어요. 기기의 Google 계정과 로그인 설정을 확인해주세요.")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: AccountException) { throw error }
        catch (_: GetCredentialException) {
            throw AccountException(AccountErrorKind.CREDENTIAL, "Google 로그인에 연결하지 못했어요. 계정과 앱 로그인 설정을 확인해주세요.")
        } catch (_: Exception) {
            throw AccountException(AccountErrorKind.CREDENTIAL, "Google 응답을 확인하지 못했어요. 다시 시도해주세요.")
        }
    }

    suspend fun clear(context: Context) {
        try { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Local opaque-session logout is independent of provider UI state. */ }
    }
}
