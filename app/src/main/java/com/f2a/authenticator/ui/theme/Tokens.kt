package com.f2a.authenticator.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Precision Deep-Blue Vault 设计 Token 体系（4dp 基准网格 + 表格等宽数字 tnum）。
 */
object Space {
    val XXS = 2.dp
    val XS = 4.dp
    val HS = 6.dp
    val S = 8.dp
    val MS = 10.dp
    val M = 12.dp
    val ML = 14.dp
    val L = 16.dp
    val XL = 20.dp
    val XXL = 24.dp
    val X3L = 32.dp
    val X4L = 40.dp
    val X5L = 48.dp
    val BottomListPad = 96.dp
}

object Radius {
    val XS = 6.dp
    val S = 10.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 22.dp
    val Full = 999.dp
}

object IconSize {
    val XS = 16.dp
    val S = 18.dp
    val M = 20.dp
    val L = 22.dp
    val XL = 24.dp
    val Hero = 48.dp
    val LockShield = 34.dp
    val LockKey = 72.dp
}

object Type {
    // 详情页大字验证码（6 位标准 44.sp，8 位窄字距自适应 36.sp）
    val CodeHero = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = 3.sp
    )

    val CodeHeroCompact = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 1.5.sp
    )

    val CodeRow = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 1.5.sp
    )

    val CodeRowCompact = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp
    )

    val CodeMonoS = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        lineHeight = 14.sp
    )

    val AvatarHero = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.5.sp
    )

    val AvatarCard = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    )

    val KeypadDigit = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp
    )

    val TitlePage = TextStyle(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.2.sp
    )

    val TitleSection = TextStyle(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    )

    val TitleCard = TextStyle(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    )

    val BodyL = TextStyle(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )

    val BodyM = TextStyle(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    val BodyS = TextStyle(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    val Label = TextStyle(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )

    val Caption = TextStyle(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 13.sp
    )
}

object Motion {
    const val instant = 0
    const val fast = 120
    const val base = 200
    const val slow = 240
    const val errorShake = 320
    const val hold = 1500L

    val EaseStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EaseDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EaseAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

object Touch {
    val MinTarget = 48.dp
    val SegmentedHeight = 40.dp
    val CompactChipHeight = 34.dp
}

object Layout {
    /** 横屏与平板上正文列的最大宽度，避免卡片与表单被拉满全宽。 */
    val ContentMaxWidth = 640.dp
}

/** 占满可用宽度，但内容列限制在 [max] 以内并水平居中。 */
fun Modifier.contentMaxWidth(max: Dp = Layout.ContentMaxWidth): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = max)
        .fillMaxWidth()

val AppTypography = Typography(
    headlineMedium = Type.TitlePage,
    headlineSmall = Type.TitleSection,
    titleLarge = Type.TitleSection,
    titleMedium = Type.TitleCard,
    titleSmall = Type.TitleCard,
    bodyLarge = Type.BodyL,
    bodyMedium = Type.BodyM,
    bodySmall = Type.BodyS,
    labelLarge = Type.BodyL,
    labelMedium = Type.Label,
    labelSmall = Type.Caption
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.XS),
    small = RoundedCornerShape(Radius.S),
    medium = RoundedCornerShape(Radius.M),
    large = RoundedCornerShape(Radius.L),
    extraLarge = RoundedCornerShape(Radius.XL)
)
