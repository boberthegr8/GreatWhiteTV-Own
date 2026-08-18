package tv.own.owntv.features.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.update.UpdateManager

@Composable
internal fun updateFailureText(failure: UpdateManager.Failure): String = when (failure) {
    is UpdateManager.Failure.CheckHttp -> stringResource(R.string.update_failed_check_http, failure.code.toString())
    UpdateManager.Failure.NoCompatibleApk -> stringResource(R.string.update_no_compatible_apk)
    UpdateManager.Failure.InvalidReleaseResponse -> stringResource(R.string.update_invalid_release_response)
    UpdateManager.Failure.CheckNetwork -> stringResource(R.string.update_failed_check)
    is UpdateManager.Failure.DownloadHttp -> stringResource(R.string.update_failed_download_http, failure.code.toString())
    UpdateManager.Failure.EmptyDownload -> stringResource(R.string.update_empty_download)
    UpdateManager.Failure.DigestMismatch -> stringResource(R.string.online_update_digest_mismatch)
    UpdateManager.Failure.DownloadNetwork -> stringResource(R.string.update_failed_download)
    UpdateManager.Failure.Install -> stringResource(R.string.update_install_failed)
}
