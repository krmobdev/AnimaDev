package com.animacode.animadev.projects

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.animacode.animadev.R

/** Official Google Material Symbols Outlined; source/license in docs. */
object AppIcons {
    val Folder:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_folder)
    val Git:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_account_tree)
    val Tests:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_science)
    val Build:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_build)
    val History:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_history)
    val Terminal:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_terminal)
    val Back:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_arrow_back)
    val More:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_more_vert)
    val Add:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_add)
    val Chevron:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_chevron_right)
    val Refresh:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_refresh)
    val Share:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_share)
    val Search:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_search)
    val Copy:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_content_copy)
    val Download:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_download)
    val Top:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_vertical_align_top)
    val Bottom:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_vertical_align_bottom)
    val Settings:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_settings)
    val Success:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_check_circle)
    val Error:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_error)
    val Info:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_info)
    val Close:ImageVector @Composable get()=ImageVector.vectorResource(R.drawable.ms_close)
}
