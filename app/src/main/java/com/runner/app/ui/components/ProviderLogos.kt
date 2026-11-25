package com.runner.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.runner.app.data.extractHost

/**
 * Фирменные векторные логотипы AI-провайдеров и семейств моделей.
 *
 * Монохромные (заливка белая), цвет задаётся через tint у Icon (например, LocalContentColor
 * или приглушённый акцент), поэтому логотипы выглядят нативно и строго в тёмной теме,
 * не превращая интерфейс в новогоднюю ёлку.
 */
object ProviderLogos {

    private fun logo(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White), pathBuilder = pathBuilder)
        }.build()

    private fun svgLogo(
        name: String,
        fillType: PathFillType = PathFillType.EvenOdd,
        vararg pathData: String
    ): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            pathData.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    pathFillType = fillType,
                    fill = SolidColor(Color.White)
                )
            }
        }.build()

    /** OpenAI */
    val OpenAI: ImageVector by lazy {
        logo("OpenAI") {
            moveTo(9.2050f, 8.6580f)
            lineTo(9.2050f, 6.3980f)
            curveTo(9.2050f, 6.2080f, 9.2770f, 6.0650f, 9.4430f, 5.9700f)
            lineTo(13.9860f, 3.3540f)
            curveTo(14.6050f, 2.9970f, 15.3420f, 2.8310f, 16.1030f, 2.8310f)
            curveTo(18.9570f, 2.8310f, 20.7650f, 5.0430f, 20.7650f, 7.3970f)
            curveTo(20.7650f, 7.5640f, 20.7650f, 7.7540f, 20.7410f, 7.9440f)
            lineTo(16.0310f, 5.1850f)
            arcTo(0.7970f, 0.7970f, 0.0000f, false, false, 15.1750f, 5.1850f)
            lineTo(9.2050f, 8.6580f)
            close()
            moveTo(19.8140f, 17.4580f)
            lineTo(19.8140f, 12.0600f)
            curveTo(19.8140f, 11.7270f, 19.6710f, 11.4900f, 19.3850f, 11.3230f)
            lineTo(13.4150f, 7.8500f)
            lineTo(15.3650f, 6.7320f)
            arcTo(0.4330f, 0.4330f, 0.0000f, false, true, 15.8410f, 6.7320f)
            lineTo(20.3840f, 9.3490f)
            curveTo(21.6930f, 10.1090f, 22.5730f, 11.7270f, 22.5730f, 13.2970f)
            curveTo(22.5730f, 15.1050f, 21.5030f, 16.7700f, 19.8130f, 17.4600f)
            close()
            moveTo(7.8020f, 12.7030f)
            lineTo(5.8520f, 11.5610f)
            curveTo(5.6850f, 11.4660f, 5.6130f, 11.3230f, 5.6130f, 11.1330f)
            lineTo(5.6130f, 5.8990f)
            curveTo(5.6130f, 3.3540f, 7.5630f, 1.4270f, 10.2040f, 1.4270f)
            curveTo(11.2040f, 1.4270f, 12.1310f, 1.7600f, 12.9160f, 2.3550f)
            lineTo(8.2300f, 5.0670f)
            curveTo(7.9450f, 5.2330f, 7.8020f, 5.4710f, 7.8020f, 5.8040f)
            lineTo(7.8020f, 12.7020f)
            close()
            moveTo(12.0000f, 15.1280f)
            lineTo(9.2050f, 13.5580f)
            lineTo(9.2050f, 10.2280f)
            lineTo(12.0000f, 8.6580f)
            lineTo(14.7950f, 10.2280f)
            lineTo(14.7950f, 13.5580f)
            lineTo(12.0000f, 15.1280f)
            close()
            moveTo(13.7960f, 22.3580f)
            curveTo(12.7960f, 22.3580f, 11.8690f, 22.0260f, 11.0840f, 21.4310f)
            lineTo(15.7700f, 18.7190f)
            curveTo(16.0550f, 18.5530f, 16.1980f, 18.3150f, 16.1980f, 17.9820f)
            lineTo(16.1980f, 11.0840f)
            lineTo(18.1720f, 12.2260f)
            curveTo(18.3390f, 12.3210f, 18.4100f, 12.4640f, 18.4100f, 12.6540f)
            lineTo(18.4100f, 17.8870f)
            curveTo(18.4100f, 20.4320f, 16.4360f, 22.3590f, 13.7960f, 22.3590f)
            close()
            moveTo(8.1590f, 17.0550f)
            lineTo(3.6150f, 14.4380f)
            curveTo(2.3070f, 13.6770f, 1.4270f, 12.0600f, 1.4270f, 10.4900f)
            arcTo(4.4820f, 4.4820f, 0.0000f, false, true, 4.2100f, 6.3270f)
            lineTo(4.2100f, 11.7500f)
            curveTo(4.2100f, 12.0830f, 4.3530f, 12.3210f, 4.6380f, 12.4880f)
            lineTo(10.5850f, 15.9370f)
            lineTo(8.6350f, 17.0550f)
            arcTo(0.4320f, 0.4320f, 0.0000f, false, true, 8.1590f, 17.0550f)
            close()
            moveTo(7.8970f, 20.9550f)
            curveTo(5.2090f, 20.9550f, 3.2350f, 18.9340f, 3.2350f, 16.4360f)
            curveTo(3.2350f, 16.2460f, 3.2590f, 16.0560f, 3.2820f, 15.8660f)
            lineTo(7.9680f, 18.5760f)
            curveTo(8.2540f, 18.7430f, 8.5390f, 18.7430f, 8.8240f, 18.5760f)
            lineTo(14.7940f, 15.1280f)
            lineTo(14.7940f, 17.3880f)
            curveTo(14.7940f, 17.5780f, 14.7240f, 17.7210f, 14.5570f, 17.8160f)
            lineTo(10.0140f, 20.4320f)
            curveTo(9.3950f, 20.7890f, 8.6580f, 20.9550f, 7.8970f, 20.9550f)
            close()
            moveTo(13.7960f, 23.7850f)
            arcTo(5.9470f, 5.9470f, 0.0000f, false, false, 19.6230f, 19.0290f)
            curveTo(22.2870f, 18.3390f, 24.0000f, 15.8400f, 24.0000f, 13.2960f)
            curveTo(24.0000f, 11.6310f, 23.2870f, 10.0140f, 22.0020f, 8.8480f)
            curveTo(22.1210f, 8.3480f, 22.1920f, 7.8490f, 22.1920f, 7.3500f)
            curveTo(22.1920f, 3.9490f, 19.4330f, 1.4030f, 16.2460f, 1.4030f)
            curveTo(15.6040f, 1.4030f, 14.9860f, 1.4980f, 14.3660f, 1.7130f)
            arcTo(5.9620f, 5.9620f, 0.0000f, false, false, 10.2050f, 0.0000f)
            arcTo(5.9470f, 5.9470f, 0.0000f, false, false, 4.3780f, 4.7570f)
            curveTo(1.7130f, 5.4470f, 0.0000f, 7.9450f, 0.0000f, 10.4900f)
            curveTo(0.0000f, 12.1560f, 0.7130f, 13.7730f, 1.9980f, 14.9380f)
            curveTo(1.8790f, 15.4380f, 1.8080f, 15.9380f, 1.8080f, 16.4370f)
            curveTo(1.8080f, 19.8380f, 4.5670f, 22.3830f, 7.7540f, 22.3830f)
            curveTo(8.3960f, 22.3830f, 9.0140f, 22.2880f, 9.6340f, 22.0740f)
            arcTo(5.9600f, 5.9600f, 0.0000f, false, false, 13.7960f, 23.7870f)
            close()
        }
    }

    /** Gemini */
    val Gemini: ImageVector by lazy {
        logo("Gemini") {
            moveTo(20.6160f, 10.8350f)
            arcTo(14.1470f, 14.1470f, 0.0000f, false, true, 16.1660f, 7.8340f)
            arcTo(14.1110f, 14.1110f, 0.0000f, false, true, 12.4880f, 1.3820f)
            arcTo(0.5030f, 0.5030f, 0.0000f, false, false, 11.5130f, 1.3820f)
            arcTo(14.1340f, 14.1340f, 0.0000f, false, true, 7.8340f, 7.8340f)
            arcTo(14.1550f, 14.1550f, 0.0000f, false, true, 3.3840f, 10.8350f)
            curveTo(2.7340f, 11.1150f, 2.0660f, 11.3400f, 1.3820f, 11.5130f)
            arcTo(0.5020f, 0.5020f, 0.0000f, false, false, 1.3820f, 12.4880f)
            curveTo(2.0660f, 12.6600f, 2.7320f, 12.8850f, 3.3840f, 13.1650f)
            arcTo(14.1470f, 14.1470f, 0.0000f, false, true, 7.8340f, 16.1660f)
            arcTo(14.1120f, 14.1120f, 0.0000f, false, true, 11.5130f, 22.6190f)
            arcTo(0.5020f, 0.5020f, 0.0000f, false, false, 12.4880f, 22.6190f)
            curveTo(12.6600f, 21.9340f, 12.8850f, 21.2680f, 13.1650f, 20.6160f)
            arcTo(14.1450f, 14.1450f, 0.0000f, false, true, 16.1660f, 16.1660f)
            arcTo(14.1130f, 14.1130f, 0.0000f, false, true, 22.6190f, 12.4880f)
            arcTo(0.5030f, 0.5030f, 0.0000f, false, false, 22.6190f, 11.5130f)
            arcTo(13.2450f, 13.2450f, 0.0000f, false, true, 20.6160f, 10.8350f)
            close()
        }
    }

    /** Claude */
    val Claude: ImageVector by lazy {
        logo("Claude") {
            moveTo(4.7144f, 15.9555f)
            lineTo(9.4318f, 13.3084f)
            lineTo(9.5108f, 13.0777f)
            lineTo(9.4318f, 12.9502f)
            lineTo(9.2011f, 12.9502f)
            lineTo(8.4118f, 12.9016f)
            lineTo(5.7162f, 12.8287f)
            lineTo(3.3787f, 12.7316f)
            lineTo(1.1141f, 12.6102f)
            lineTo(0.5434f, 12.4887f)
            lineTo(0.0091f, 11.7845f)
            lineTo(0.0637f, 11.4323f)
            lineTo(0.5434f, 11.1105f)
            lineTo(1.2294f, 11.1713f)
            lineTo(2.7473f, 11.2745f)
            lineTo(5.0240f, 11.4323f)
            lineTo(6.6754f, 11.5295f)
            lineTo(9.1222f, 11.7845f)
            lineTo(9.5108f, 11.7845f)
            lineTo(9.5654f, 11.6266f)
            lineTo(9.4318f, 11.5295f)
            lineTo(9.3286f, 11.4323f)
            lineTo(6.9730f, 9.8356f)
            lineTo(4.4230f, 8.1477f)
            lineTo(3.0874f, 7.1763f)
            lineTo(2.3649f, 6.6845f)
            lineTo(2.0006f, 6.2231f)
            lineTo(1.8428f, 5.2153f)
            lineTo(2.4985f, 4.4928f)
            lineTo(3.3788f, 4.5535f)
            lineTo(3.6034f, 4.6142f)
            lineTo(4.4959f, 5.3002f)
            lineTo(6.4023f, 6.7756f)
            lineTo(8.8916f, 8.6092f)
            lineTo(9.2559f, 8.9127f)
            lineTo(9.4016f, 8.8095f)
            lineTo(9.4198f, 8.7367f)
            lineTo(9.2558f, 8.4634f)
            lineTo(7.9019f, 6.0167f)
            lineTo(6.4569f, 3.5274f)
            lineTo(5.8134f, 2.4954f)
            lineTo(5.6434f, 1.8760f)
            curveTo(5.5827f, 1.6210f, 5.5402f, 1.4086f, 5.5402f, 1.1475f)
            lineTo(6.2870f, 0.1335f)
            lineTo(6.6997f, 0.0000f)
            lineTo(7.6954f, 0.1336f)
            lineTo(8.1144f, 0.4978f)
            lineTo(8.7336f, 1.9125f)
            lineTo(9.7354f, 4.1407f)
            lineTo(11.2897f, 7.1703f)
            lineTo(11.7450f, 8.0688f)
            lineTo(11.9879f, 8.9006f)
            lineTo(12.0789f, 9.1556f)
            lineTo(12.2368f, 9.1556f)
            lineTo(12.2368f, 9.0099f)
            lineTo(12.3643f, 7.3039f)
            lineTo(12.6011f, 5.2092f)
            lineTo(12.8318f, 2.5135f)
            lineTo(12.9107f, 1.7546f)
            lineTo(13.2871f, 0.8439f)
            lineTo(14.0339f, 0.3521f)
            lineTo(14.6167f, 0.6314f)
            lineTo(15.0964f, 1.3174f)
            lineTo(15.0296f, 1.7607f)
            lineTo(14.7443f, 3.6124f)
            lineTo(14.1857f, 6.5145f)
            lineTo(13.8214f, 8.4574f)
            lineTo(14.0339f, 8.4574f)
            lineTo(14.2768f, 8.2145f)
            lineTo(15.2603f, 6.9092f)
            lineTo(16.9117f, 4.8449f)
            lineTo(17.6403f, 4.0253f)
            lineTo(18.4903f, 3.1207f)
            lineTo(19.0367f, 2.6896f)
            lineTo(20.0688f, 2.6896f)
            lineTo(20.8278f, 3.8189f)
            lineTo(20.4878f, 4.9846f)
            lineTo(19.4253f, 6.3324f)
            lineTo(18.5449f, 7.4738f)
            lineTo(17.2821f, 9.1738f)
            lineTo(16.4928f, 10.5338f)
            lineTo(16.5657f, 10.6431f)
            lineTo(16.7539f, 10.6248f)
            lineTo(19.6074f, 10.0178f)
            lineTo(21.1495f, 9.7384f)
            lineTo(22.9891f, 9.4227f)
            lineTo(23.8209f, 9.8113f)
            lineTo(23.9119f, 10.2059f)
            lineTo(23.5841f, 11.0134f)
            lineTo(21.6171f, 11.4991f)
            lineTo(19.3099f, 11.9605f)
            lineTo(15.8735f, 12.7741f)
            lineTo(15.8310f, 12.8045f)
            lineTo(15.8796f, 12.8652f)
            lineTo(17.4278f, 13.0109f)
            lineTo(18.0896f, 13.0473f)
            lineTo(19.7106f, 13.0473f)
            lineTo(22.7281f, 13.2720f)
            lineTo(23.5173f, 13.7940f)
            lineTo(23.9909f, 14.4316f)
            lineTo(23.9119f, 14.9173f)
            lineTo(22.6977f, 15.5366f)
            lineTo(21.0584f, 15.1480f)
            lineTo(17.2334f, 14.2373f)
            lineTo(15.9221f, 13.9094f)
            lineTo(15.7399f, 13.9094f)
            lineTo(15.7399f, 14.0187f)
            lineTo(16.8328f, 15.0873f)
            lineTo(18.8363f, 16.8965f)
            lineTo(21.3438f, 19.2279f)
            lineTo(21.4713f, 19.8047f)
            lineTo(21.1495f, 20.2601f)
            lineTo(20.8095f, 20.2115f)
            lineTo(18.6056f, 18.5540f)
            lineTo(17.7556f, 17.8072f)
            lineTo(15.8310f, 16.1862f)
            lineTo(15.7035f, 16.1862f)
            lineTo(15.7035f, 16.3562f)
            lineTo(16.1467f, 17.0058f)
            lineTo(18.4903f, 20.5272f)
            lineTo(18.6117f, 21.6079f)
            lineTo(18.4417f, 21.9600f)
            lineTo(17.8346f, 22.1725f)
            lineTo(17.1667f, 22.0511f)
            lineTo(15.7946f, 20.1265f)
            lineTo(14.3800f, 17.9590f)
            lineTo(13.2386f, 16.0162f)
            lineTo(13.0989f, 16.0952f)
            lineTo(12.4249f, 23.3504f)
            lineTo(12.1093f, 23.7207f)
            lineTo(11.3807f, 24.0000f)
            lineTo(10.7736f, 23.5386f)
            lineTo(10.4518f, 22.7918f)
            lineTo(10.7736f, 21.3165f)
            lineTo(11.1622f, 19.3919f)
            lineTo(11.4779f, 17.8619f)
            lineTo(11.7632f, 15.9615f)
            lineTo(11.9332f, 15.3301f)
            lineTo(11.9211f, 15.2876f)
            lineTo(11.7814f, 15.3058f)
            lineTo(10.3486f, 17.2730f)
            lineTo(8.1690f, 20.2176f)
            lineTo(6.4447f, 22.0632f)
            lineTo(6.0319f, 22.2272f)
            lineTo(5.3155f, 21.8568f)
            lineTo(5.3822f, 21.1950f)
            lineTo(5.7830f, 20.6061f)
            lineTo(8.1690f, 17.5704f)
            lineTo(9.6079f, 15.6884f)
            lineTo(10.5369f, 14.6016f)
            lineTo(10.5307f, 14.4437f)
            lineTo(10.4761f, 14.4437f)
            lineTo(4.1376f, 18.5601f)
            lineTo(3.0083f, 18.7058f)
            lineTo(2.5226f, 18.2504f)
            lineTo(2.5834f, 17.5037f)
            lineTo(2.8141f, 17.2608f)
            lineTo(4.7205f, 15.9494f)
            close()
        }
    }

    /** DeepSeek */
    val DeepSeek: ImageVector by lazy {
        logo("DeepSeek") {
            moveTo(23.7480f, 4.4820f)
            curveTo(23.4940f, 4.3580f, 23.3840f, 4.5950f, 23.2360f, 4.7160f)
            curveTo(23.1850f, 4.7550f, 23.1420f, 4.8060f, 23.0990f, 4.8520f)
            curveTo(22.7270f, 5.2490f, 22.2930f, 5.5090f, 21.7260f, 5.4780f)
            curveTo(20.8970f, 5.4320f, 20.1890f, 5.6920f, 19.5630f, 6.3260f)
            curveTo(19.4300f, 5.5440f, 18.9880f, 5.0780f, 18.3160f, 4.7780f)
            curveTo(17.9640f, 4.6220f, 17.6080f, 4.4670f, 17.3610f, 4.1280f)
            curveTo(17.1890f, 3.8870f, 17.1420f, 3.6180f, 17.0560f, 3.3540f)
            curveTo(17.0010f, 3.1940f, 16.9460f, 3.0310f, 16.7630f, 3.0040f)
            curveTo(16.5630f, 2.9730f, 16.4850f, 3.1400f, 16.4070f, 3.2800f)
            curveTo(16.0940f, 3.8520f, 15.9730f, 4.4820f, 15.9850f, 5.1200f)
            curveTo(16.0120f, 6.5560f, 16.6180f, 7.7000f, 17.8230f, 8.5130f)
            curveTo(17.9600f, 8.6060f, 17.9950f, 8.7000f, 17.9520f, 8.8360f)
            curveTo(17.8700f, 9.1160f, 17.7720f, 9.3880f, 17.6860f, 9.6690f)
            curveTo(17.6310f, 9.8480f, 17.5490f, 9.8860f, 17.3570f, 9.8090f)
            arcTo(5.5260f, 5.5260f, 0.0000f, false, true, 15.6210f, 8.6290f)
            curveTo(14.7640f, 7.8010f, 13.9900f, 6.8870f, 13.0240f, 6.1710f)
            arcTo(11.3650f, 11.3650f, 0.0000f, false, false, 12.3350f, 5.7000f)
            curveTo(11.3500f, 4.7430f, 12.4650f, 3.9570f, 12.7230f, 3.8640f)
            curveTo(12.9930f, 3.7660f, 12.8160f, 3.4320f, 11.9440f, 3.4360f)
            curveTo(11.0720f, 3.4400f, 10.2740f, 3.7310f, 9.2570f, 4.1200f)
            arcTo(3.0550f, 3.0550f, 0.0000f, false, true, 8.7920f, 4.2570f)
            arcTo(9.5970f, 9.5970f, 0.0000f, false, false, 5.9090f, 4.1550f)
            curveTo(4.0240f, 4.3650f, 2.5190f, 5.2570f, 1.4120f, 6.7780f)
            curveTo(0.0820f, 8.6060f, -0.2310f, 10.6840f, 0.1520f, 12.8500f)
            curveTo(0.5550f, 15.1340f, 1.7210f, 17.0250f, 3.5120f, 18.5030f)
            curveTo(5.3700f, 20.0360f, 7.5090f, 20.7870f, 9.9500f, 20.6430f)
            curveTo(11.4320f, 20.5580f, 13.0830f, 20.3590f, 14.9440f, 18.7830f)
            curveTo(15.4140f, 19.0170f, 15.9060f, 19.1100f, 16.7240f, 19.1800f)
            curveTo(17.3540f, 19.2390f, 17.9600f, 19.1500f, 18.4290f, 19.0520f)
            curveTo(19.1640f, 18.8960f, 19.1130f, 18.2150f, 18.8480f, 18.0910f)
            curveTo(16.6930f, 17.0870f, 17.1660f, 17.4960f, 16.7350f, 17.1650f)
            curveTo(17.8310f, 15.8690f, 19.4810f, 14.5230f, 20.1270f, 10.1620f)
            curveTo(20.1770f, 9.8150f, 20.1340f, 9.5970f, 20.1270f, 9.3170f)
            curveTo(20.1230f, 9.1470f, 20.1620f, 9.0800f, 20.3570f, 9.0610f)
            arcTo(4.1730f, 4.1730f, 0.0000f, false, false, 21.9020f, 8.5860f)
            curveTo(23.2980f, 7.8230f, 23.8620f, 6.5710f, 23.9950f, 5.0690f)
            curveTo(24.0150f, 4.8390f, 23.9910f, 4.6020f, 23.7480f, 4.4810f)
            close()
            moveTo(11.5810f, 18.0000f)
            curveTo(9.4920f, 16.3580f, 8.4790f, 15.8170f, 8.0610f, 15.8400f)
            curveTo(7.6690f, 15.8640f, 7.7400f, 16.3110f, 7.8260f, 16.6030f)
            curveTo(7.9160f, 16.8910f, 8.0330f, 17.0890f, 8.1970f, 17.3420f)
            curveTo(8.3110f, 17.5090f, 8.3890f, 17.7580f, 8.0840f, 17.9450f)
            curveTo(7.4110f, 18.3610f, 6.2420f, 17.8050f, 6.1870f, 17.7780f)
            curveTo(4.8260f, 16.9760f, 3.6870f, 15.9180f, 2.8860f, 14.4710f)
            curveTo(2.1120f, 13.0780f, 1.6620f, 11.5840f, 1.5880f, 9.9890f)
            curveTo(1.5680f, 9.6030f, 1.6810f, 9.4670f, 2.0650f, 9.3970f)
            arcTo(4.6960f, 4.6960f, 0.0000f, false, true, 3.5940f, 9.3580f)
            curveTo(5.7260f, 9.6700f, 7.5400f, 10.6230f, 9.0620f, 12.1320f)
            curveTo(9.9300f, 12.9920f, 10.5870f, 14.0190f, 11.2640f, 15.0230f)
            curveTo(11.9840f, 16.0890f, 12.7580f, 17.1050f, 13.7440f, 17.9370f)
            curveTo(14.0920f, 18.2290f, 14.3690f, 18.4510f, 14.6350f, 18.6140f)
            curveTo(13.8330f, 18.7040f, 12.4950f, 18.7240f, 11.5810f, 18.0000f)
            close()
            moveTo(12.5810f, 11.5600f)
            arcTo(0.3060f, 0.3060f, 0.0000f, false, true, 12.9960f, 11.2730f)
            arcTo(0.3020f, 0.3020f, 0.0000f, false, true, 13.1960f, 11.5610f)
            arcTo(0.3060f, 0.3060f, 0.0000f, false, true, 12.8860f, 11.8680f)
            arcTo(0.3030f, 0.3030f, 0.0000f, false, true, 12.5820f, 11.5600f)
            close()
            moveTo(15.6910f, 13.1560f)
            curveTo(15.4910f, 13.2370f, 15.2920f, 13.3070f, 15.1010f, 13.3160f)
            arcTo(1.2450f, 1.2450f, 0.0000f, false, true, 14.3030f, 13.0620f)
            curveTo(14.0290f, 12.8320f, 13.8330f, 12.7040f, 13.7510f, 12.3040f)
            arcTo(1.7300f, 1.7300f, 0.0000f, false, true, 13.7670f, 11.7160f)
            curveTo(13.8370f, 11.3890f, 13.7590f, 11.1790f, 13.5280f, 10.9890f)
            curveTo(13.3410f, 10.8330f, 13.1020f, 10.7900f, 12.8400f, 10.7900f)
            arcTo(0.5590f, 0.5590f, 0.0000f, false, true, 12.5860f, 10.7120f)
            curveTo(12.4760f, 10.6580f, 12.3860f, 10.5220f, 12.4720f, 10.3540f)
            curveTo(12.5000f, 10.3000f, 12.6320f, 10.1680f, 12.6640f, 10.1440f)
            curveTo(13.0200f, 9.9420f, 13.4310f, 10.0080f, 13.8100f, 10.1600f)
            curveTo(14.1620f, 10.3040f, 14.4280f, 10.5680f, 14.8110f, 10.9420f)
            curveTo(15.2020f, 11.3930f, 15.2730f, 11.5180f, 15.4960f, 11.8560f)
            curveTo(15.6720f, 12.1210f, 15.8320f, 12.3930f, 15.9410f, 12.7040f)
            curveTo(16.0080f, 12.8990f, 15.9220f, 13.0580f, 15.6910f, 13.1560f)
            close()
        }
    }

    /** Groq */
    val Groq: ImageVector by lazy {
        logo("Groq") {
            moveTo(12.0360f, 2.0000f)
            curveTo(8.1830f, 1.9650f, 5.0360f, 5.0000f, 5.0000f, 8.7810f)
            curveTo(4.9650f, 12.5630f, 8.0550f, 15.6530f, 11.9080f, 15.6880f)
            lineTo(14.3280f, 15.6880f)
            lineTo(14.3280f, 13.1220f)
            lineTo(12.0360f, 13.1220f)
            curveTo(9.6290f, 13.1500f, 7.6560f, 11.2560f, 7.6280f, 8.8920f)
            curveTo(7.5990f, 6.5300f, 9.5290f, 4.5940f, 11.9360f, 4.5660f)
            lineTo(12.0360f, 4.5660f)
            curveTo(14.4430f, 4.5660f, 16.3940f, 6.4810f, 16.4010f, 8.8440f)
            lineTo(16.4010f, 15.1490f)
            curveTo(16.4010f, 17.4910f, 14.4570f, 19.3990f, 12.0780f, 19.4280f)
            arcTo(4.3750f, 4.3750f, 0.0000f, false, true, 9.0450f, 18.1760f)
            lineTo(7.1940f, 19.9940f)
            arcTo(7.0000f, 7.0000f, 0.0000f, false, false, 12.0290f, 22.0000f)
            lineTo(12.1210f, 22.0000f)
            curveTo(15.9240f, 21.9440f, 18.9790f, 18.9170f, 19.0000f, 15.1840f)
            lineTo(19.0000f, 8.6840f)
            curveTo(18.9070f, 4.9630f, 15.8170f, 2.0000f, 12.0360f, 2.0000f)
            close()
        }
    }

    /** Meta */
    val Meta: ImageVector by lazy {
        logo("Meta") {
            moveTo(6.8970f, 4.0000f)
            curveTo(8.8120f, 4.0000f, 10.4130f, 4.9320f, 12.3270f, 7.3760f)
            lineTo(12.6090f, 7.0030f)
            curveTo(12.7990f, 6.7570f, 12.9920f, 6.5190f, 13.1890f, 6.2930f)
            lineTo(13.5020f, 5.9430f)
            curveTo(14.5880f, 4.7880f, 15.7920f, 4.0000f, 17.2250f, 4.0000f)
            curveTo(18.4980f, 4.0000f, 19.6940f, 4.5570f, 20.7160f, 5.5160f)
            lineTo(20.9340f, 5.7290f)
            curveTo(22.6640f, 7.4940f, 23.8510f, 10.4390f, 23.9870f, 13.7550f)
            lineTo(23.9980f, 14.1470f)
            lineTo(24.0000f, 14.3970f)
            curveTo(24.0000f, 15.8980f, 23.7200f, 17.1560f, 23.1820f, 18.0970f)
            lineTo(23.0420f, 18.3270f)
            lineTo(22.9340f, 18.4800f)
            curveTo(22.6330f, 18.9000f, 22.2700f, 19.2380f, 21.8480f, 19.4890f)
            lineTo(21.5830f, 19.6310f)
            lineTo(21.4960f, 19.6710f)
            arcTo(3.4930f, 3.4930f, 0.0000f, false, true, 21.1940f, 19.7890f)
            arcTo(4.1170f, 4.1170f, 0.0000f, false, true, 19.8640f, 19.9970f)
            curveTo(19.3400f, 19.9970f, 18.8680f, 19.9300f, 18.4260f, 19.7820f)
            curveTo(17.8120f, 19.5780f, 17.2630f, 19.2220f, 16.7000f, 18.6660f)
            lineTo(16.4730f, 18.4310f)
            curveTo(15.7200f, 17.6190f, 14.9390f, 16.4550f, 13.9800f, 14.8450f)
            lineTo(12.5500f, 12.4350f)
            lineTo(12.0060f, 11.5400f)
            lineTo(10.2400f, 14.6700f)
            lineTo(9.8970f, 15.2620f)
            curveTo(7.5970f, 19.1560f, 6.2270f, 20.0000f, 4.3560f, 20.0000f)
            curveTo(3.1460f, 20.0000f, 2.1510f, 19.5800f, 1.4200f, 18.8180f)
            lineTo(1.2520f, 18.6340f)
            curveTo(0.7680f, 18.0610f, 0.4150f, 17.3230f, 0.2090f, 16.4450f)
            lineTo(0.1420f, 16.1250f)
            arcTo(8.6900f, 8.6900f, 0.0000f, false, true, 0.0060f, 14.8370f)
            lineTo(0.0000f, 14.4680f)
            curveTo(0.0020f, 13.7230f, 0.0600f, 12.9780f, 0.1740f, 12.2380f)
            lineTo(0.2740f, 11.6650f)
            curveTo(0.5720f, 10.1350f, 1.1020f, 8.7070f, 1.8100f, 7.5080f)
            lineTo(2.0190f, 7.1680f)
            curveTo(3.1960f, 5.3380f, 4.8080f, 4.1150f, 6.6340f, 4.0080f)
            lineTo(6.8970f, 4.0000f)
            close()
            moveTo(6.8640f, 6.6150f)
            lineTo(6.6630f, 6.6250f)
            curveTo(5.8330f, 6.7080f, 5.0570f, 7.2980f, 4.4110f, 8.2020f)
            lineTo(4.2730f, 8.4010f)
            lineTo(4.2630f, 8.4190f)
            curveTo(3.5930f, 9.4360f, 3.0780f, 10.7970f, 2.8070f, 12.2640f)
            lineTo(2.8030f, 12.2860f)
            arcTo(12.5910f, 12.5910f, 0.0000f, false, false, 2.5960f, 14.5400f)
            lineTo(2.5980f, 14.7280f)
            curveTo(2.6020f, 14.9080f, 2.6150f, 15.0880f, 2.6380f, 15.2680f)
            lineTo(2.6810f, 15.5590f)
            curveTo(2.7730f, 16.0620f, 2.9380f, 16.4670f, 3.1670f, 16.7670f)
            lineTo(3.2840f, 16.9040f)
            curveTo(3.5870f, 17.2270f, 3.9820f, 17.3960f, 4.4540f, 17.3960f)
            curveTo(5.5540f, 17.3960f, 6.2500f, 16.7200f, 8.1500f, 13.7550f)
            lineTo(10.3250f, 10.3550f)
            lineTo(10.7790f, 9.6540f)
            lineTo(10.6400f, 9.4560f)
            curveTo(9.1100f, 7.3000f, 8.0840f, 6.6160f, 6.8640f, 6.6160f)
            close()
            moveTo(17.0600f, 6.0630f)
            lineTo(16.8840f, 6.0700f)
            curveTo(16.2490f, 6.1180f, 15.6610f, 6.4290f, 15.0640f, 7.0030f)
            lineTo(14.8680f, 7.2010f)
            curveTo(14.4290f, 7.6630f, 13.9810f, 8.2650f, 13.5010f, 9.0080f)
            lineTo(13.7670f, 9.4060f)
            curveTo(13.9470f, 9.6800f, 14.1290f, 9.9660f, 14.3170f, 10.2640f)
            lineTo(14.6100f, 10.7390f)
            lineTo(16.0060f, 13.0740f)
            lineTo(16.7010f, 14.1880f)
            curveTo(17.2840f, 15.1140f, 17.7310f, 15.7880f, 18.1090f, 16.2700f)
            lineTo(18.3220f, 16.5320f)
            curveTo(18.6040f, 16.8580f, 18.8510f, 17.0720f, 19.0990f, 17.2050f)
            lineTo(19.2010f, 17.2550f)
            curveTo(19.4280f, 17.3550f, 19.6580f, 17.3930f, 19.9190f, 17.3930f)
            curveTo(20.0950f, 17.3950f, 20.2690f, 17.3700f, 20.4370f, 17.3200f)
            curveTo(20.7750f, 17.2160f, 21.0470f, 17.0000f, 21.2500f, 16.6830f)
            lineTo(21.3450f, 16.5200f)
            lineTo(21.4220f, 16.3580f)
            curveTo(21.6160f, 15.8990f, 21.7120f, 15.2980f, 21.7120f, 14.5730f)
            lineTo(21.7060f, 14.1240f)
            curveTo(21.6260f, 11.2530f, 20.7680f, 8.7520f, 19.5060f, 7.3260f)
            lineTo(19.3300f, 7.1370f)
            curveTo(18.6600f, 6.4540f, 17.8860f, 6.0630f, 17.0600f, 6.0630f)
            close()
        }
    }

    /** Mistral */
    val Mistral: ImageVector by lazy {
        logo("Mistral") {
            moveTo(3.4280f, 3.4000f)
            lineTo(6.8570f, 3.4000f)
            lineTo(6.8570f, 6.8280f)
            lineTo(10.2860f, 6.8280f)
            lineTo(10.2860f, 10.2570f)
            lineTo(10.2840f, 10.2570f)
            lineTo(13.7150f, 10.2570f)
            lineTo(13.7150f, 6.8280f)
            lineTo(17.1420f, 6.8280f)
            lineTo(17.1420f, 3.4000f)
            lineTo(20.5720f, 3.4000f)
            lineTo(20.5720f, 17.1140f)
            lineTo(24.0000f, 17.1140f)
            lineTo(24.0000f, 20.5430f)
            lineTo(13.7140f, 20.5430f)
            lineTo(13.7140f, 17.1150f)
            lineTo(10.2860f, 17.1150f)
            lineTo(10.2860f, 13.6860f)
            lineTo(6.8560f, 13.6860f)
            lineTo(6.8560f, 17.1140f)
            lineTo(10.2860f, 17.1140f)
            lineTo(10.2860f, 20.5430f)
            lineTo(0.0000f, 20.5430f)
            lineTo(0.0000f, 17.1140f)
            lineTo(3.4280f, 17.1140f)
            lineTo(3.4280f, 3.4000f)
            close()
            moveTo(13.7140f, 17.1150f)
            lineTo(17.1420f, 17.1150f)
            lineTo(17.1420f, 13.6860f)
            lineTo(13.7150f, 13.6860f)
            lineTo(13.7150f, 17.1150f)
            close()
        }
    }

    /** OpenRouter */
    val OpenRouter: ImageVector by lazy {
        logo("OpenRouter") {
            moveTo(18.6540f, 3.8700f)
            arcTo(5.0870f, 5.0870f, 0.0000f, true, true, 18.6540f, 14.0440f)
            lineTo(23.7000f, 19.0900f)
            curveTo(24.3400f, 19.7310f, 23.8870f, 20.8270f, 22.9800f, 20.8270f)
            lineTo(8.4800f, 20.8270f)
            arcTo(8.4790f, 8.4790f, 0.0000f, false, true, 8.4800f, 3.8690f)
            lineTo(18.6550f, 3.8690f)
            close()
            moveTo(8.4790f, 7.2600f)
            arcTo(5.0870f, 5.0870f, 0.0000f, true, false, 8.4790f, 17.4360f)
            arcTo(5.0870f, 5.0870f, 0.0000f, false, false, 8.4790f, 7.2610f)
            close()
        }
    }

    /** Qwen */
    val Qwen: ImageVector by lazy {
        logo("Qwen") {
            moveTo(12.6040f, 1.3400f)
            curveTo(12.9970f, 2.0300f, 13.3880f, 2.7220f, 13.7780f, 3.4150f)
            arcTo(0.1800f, 0.1800f, 0.0000f, false, false, 13.9350f, 3.5060f)
            lineTo(19.4870f, 3.5060f)
            curveTo(19.6610f, 3.5060f, 19.8090f, 3.6160f, 19.9330f, 3.8330f)
            lineTo(21.3870f, 6.4030f)
            curveTo(21.5770f, 6.7400f, 21.6270f, 6.8810f, 21.4110f, 7.2400f)
            curveTo(21.1510f, 7.6700f, 20.8980f, 8.1040f, 20.6510f, 8.5400f)
            lineTo(20.2840f, 9.1980f)
            curveTo(20.1780f, 9.3940f, 20.0610f, 9.4780f, 20.2440f, 9.7100f)
            lineTo(22.8960f, 14.3470f)
            curveTo(23.0680f, 14.6480f, 23.0070f, 14.8410f, 22.8530f, 15.1170f)
            curveTo(22.4160f, 15.9020f, 21.9710f, 16.6810f, 21.5180f, 17.4570f)
            curveTo(21.3590f, 17.7290f, 21.1660f, 17.8320f, 20.8380f, 17.8270f)
            curveTo(20.0610f, 17.8110f, 19.2860f, 17.8170f, 18.5110f, 17.8430f)
            arcTo(0.0990f, 0.0990f, 0.0000f, false, false, 18.4300f, 17.8930f)
            arcTo(575.0970f, 575.0970f, 0.0000f, false, true, 15.7250f, 22.6330f)
            curveTo(15.5560f, 22.9260f, 15.3450f, 22.9960f, 15.0000f, 22.9970f)
            curveTo(14.0030f, 23.0000f, 12.9980f, 23.0010f, 11.9830f, 22.9990f)
            arcTo(0.5370f, 0.5370f, 0.0000f, false, true, 11.5180f, 22.7280f)
            lineTo(10.1830f, 20.4050f)
            arcTo(0.0900f, 0.0900f, 0.0000f, false, false, 10.1000f, 20.3560f)
            lineTo(4.9820f, 20.3560f)
            curveTo(4.6970f, 20.3860f, 4.4290f, 20.3550f, 4.1770f, 20.2640f)
            lineTo(2.5740f, 17.4940f)
            arcTo(0.5430f, 0.5430f, 0.0000f, false, true, 2.5720f, 16.9540f)
            lineTo(3.7790f, 14.8340f)
            arcTo(0.1980f, 0.1980f, 0.0000f, false, false, 3.7790f, 14.6370f)
            arcTo(550.9510f, 550.9510f, 0.0000f, false, true, 1.9040f, 11.3650f)
            lineTo(1.1140f, 9.9700f)
            curveTo(0.9540f, 9.6600f, 0.9410f, 9.4740f, 1.2090f, 9.0050f)
            curveTo(1.6740f, 8.1920f, 2.1360f, 7.3800f, 2.5960f, 6.5690f)
            curveTo(2.7280f, 6.3350f, 2.9000f, 6.2350f, 3.1800f, 6.2340f)
            arcTo(338.3000f, 338.3000f, 0.0000f, false, true, 5.7690f, 6.2330f)
            arcTo(0.1240f, 0.1240f, 0.0000f, false, false, 5.8760f, 6.1700f)
            lineTo(8.6820f, 1.2750f)
            arcTo(0.4880f, 0.4880f, 0.0000f, false, true, 9.1040f, 1.0290f)
            curveTo(9.6280f, 1.0280f, 10.1570f, 1.0290f, 10.6870f, 1.0230f)
            lineTo(11.7040f, 1.0000f)
            curveTo(12.0450f, 0.9970f, 12.4280f, 1.0320f, 12.6040f, 1.3400f)
            close()
            moveTo(9.1720f, 1.7430f)
            arcTo(0.0600f, 0.0600f, 0.0000f, false, false, 9.1200f, 1.7730f)
            lineTo(6.2540f, 6.7880f)
            arcTo(0.1570f, 0.1570f, 0.0000f, false, true, 6.1190f, 6.8660f)
            lineTo(3.2530f, 6.8660f)
            curveTo(3.1970f, 6.8660f, 3.1830f, 6.8910f, 3.2120f, 6.9400f)
            lineTo(9.0220f, 17.0960f)
            curveTo(9.0470f, 17.1380f, 9.0350f, 17.1580f, 8.9880f, 17.1590f)
            lineTo(6.1930f, 17.1740f)
            arcTo(0.2180f, 0.2180f, 0.0000f, false, false, 5.9930f, 17.2900f)
            lineTo(4.6730f, 19.6000f)
            curveTo(4.6290f, 19.6780f, 4.6520f, 19.7180f, 4.7410f, 19.7180f)
            lineTo(10.4570f, 19.7260f)
            curveTo(10.5030f, 19.7260f, 10.5370f, 19.7460f, 10.5610f, 19.7870f)
            lineTo(11.9640f, 22.2410f)
            curveTo(12.0100f, 22.3220f, 12.0560f, 22.3230f, 12.1030f, 22.2410f)
            lineTo(17.1090f, 13.4810f)
            lineTo(17.8920f, 12.0990f)
            arcTo(0.0550f, 0.0550f, 0.0000f, false, true, 17.9880f, 12.0990f)
            lineTo(19.4120f, 14.6290f)
            arcTo(0.1220f, 0.1220f, 0.0000f, false, false, 19.5190f, 14.6910f)
            lineTo(22.2820f, 14.6710f)
            arcTo(0.0400f, 0.0400f, 0.0000f, false, false, 22.3170f, 14.6510f)
            arcTo(0.0410f, 0.0410f, 0.0000f, false, false, 22.3170f, 14.6110f)
            lineTo(19.4170f, 9.5250f)
            arcTo(0.1080f, 0.1080f, 0.0000f, false, true, 19.4170f, 9.4120f)
            lineTo(19.7100f, 8.9050f)
            lineTo(20.8300f, 6.9280f)
            curveTo(20.8540f, 6.8870f, 20.8420f, 6.8660f, 20.7950f, 6.8660f)
            lineTo(9.2000f, 6.8660f)
            curveTo(9.1410f, 6.8660f, 9.1270f, 6.8400f, 9.1570f, 6.7890f)
            lineTo(10.5910f, 4.2840f)
            arcTo(0.1070f, 0.1070f, 0.0000f, false, false, 10.5910f, 4.1700f)
            lineTo(9.2250f, 1.7740f)
            arcTo(0.0600f, 0.0600f, 0.0000f, false, false, 9.1720f, 1.7430f)
            close()
            moveTo(15.4620f, 9.7630f)
            curveTo(15.5080f, 9.7630f, 15.5200f, 9.7830f, 15.4960f, 9.8230f)
            lineTo(14.6640f, 11.2880f)
            lineTo(12.0510f, 15.8730f)
            arcTo(0.0560f, 0.0560f, 0.0000f, false, true, 12.0010f, 15.9020f)
            arcTo(0.0580f, 0.0580f, 0.0000f, false, true, 11.9510f, 15.8730f)
            lineTo(8.4980f, 9.8410f)
            curveTo(8.4780f, 9.8070f, 8.4880f, 9.7890f, 8.5260f, 9.7870f)
            lineTo(8.7420f, 9.7750f)
            lineTo(15.4640f, 9.7630f)
            close()
        }
    }

    /** Perplexity */
    val Perplexity: ImageVector by lazy {
        logo("Perplexity") {
            moveTo(19.7850f, 0.0000f)
            lineTo(19.7850f, 7.2720f)
            lineTo(22.5000f, 7.2720f)
            lineTo(22.5000f, 17.6200f)
            lineTo(19.5650f, 17.6200f)
            lineTo(19.5650f, 24.0000f)
            lineTo(12.5280f, 17.8060f)
            lineTo(12.5280f, 23.9510f)
            lineTo(11.4370f, 23.9510f)
            lineTo(11.4370f, 17.7990f)
            lineTo(4.3920f, 24.0000f)
            lineTo(4.3920f, 17.5350f)
            lineTo(1.5000f, 17.5350f)
            lineTo(1.5000f, 7.1880f)
            lineTo(4.3840f, 7.1880f)
            lineTo(4.3840f, 0.0000f)
            lineTo(11.4370f, 6.4940f)
            lineTo(11.4370f, 0.1900f)
            lineTo(12.5270f, 0.1900f)
            lineTo(12.5270f, 6.6800f)
            lineTo(19.7860f, 0.0000f)
            close()
            moveTo(12.5280f, 9.0440f)
            lineTo(12.5280f, 16.3630f)
            lineTo(18.4740f, 21.5970f)
            lineTo(18.4740f, 14.4400f)
            lineTo(12.5280f, 9.0430f)
            close()
            moveTo(11.4290f, 8.9640f)
            lineTo(5.4830f, 14.3620f)
            lineTo(5.4830f, 21.5970f)
            lineTo(11.4290f, 16.3630f)
            lineTo(11.4290f, 8.9650f)
            close()
            moveTo(19.5650f, 16.5440f)
            lineTo(21.4090f, 16.5440f)
            lineTo(21.4090f, 8.3490f)
            lineTo(13.4600f, 8.3490f)
            lineTo(19.5650f, 13.8890f)
            lineTo(19.5650f, 16.5440f)
            close()
            moveTo(10.5830f, 8.2640f)
            lineTo(2.5900f, 8.2640f)
            lineTo(2.5900f, 16.4590f)
            lineTo(4.3900f, 16.4590f)
            lineTo(4.3900f, 13.8830f)
            lineTo(10.5820f, 8.2630f)
            close()
            moveTo(5.4750f, 2.4760f)
            lineTo(5.4750f, 7.1860f)
            lineTo(10.5900f, 7.1860f)
            lineTo(5.4750f, 2.4760f)
            close()
            moveTo(18.6940f, 2.4760f)
            lineTo(13.5790f, 7.1860f)
            lineTo(18.6940f, 7.1860f)
            lineTo(18.6940f, 2.4760f)
            close()
        }
    }


    /** Token Harbor: монограмма T. */
    val TokenHarbor: ImageVector by lazy {
        logo("TokenHarbor") {
            moveTo(4.5f, 4f)
            lineTo(19.5f, 4f)
            lineTo(19.5f, 7f)
            lineTo(13.5f, 7f)
            lineTo(13.5f, 20f)
            lineTo(10.5f, 20f)
            lineTo(10.5f, 7f)
            lineTo(4.5f, 7f)
            close()
        }
    }

    /** Clean API: монограмма C. */
    val CleanApi: ImageVector by lazy {
        logo("CleanApi") {
            moveTo(16.93f, 5.70f)
            arcTo(8f, 8f, 0f, true, false, 16.93f, 18.30f)
            lineTo(15.02f, 15.86f)
            arcTo(4.9f, 4.9f, 0f, true, true, 15.02f, 8.14f)
            close()
        }
    }

    /** AbniBots: монограмма A. */
    val AbniBots: ImageVector by lazy {
        logo("AbniBots") {
            moveTo(12f, 3.2f)
            lineTo(21.2f, 20.8f)
            lineTo(17.6f, 20.8f)
            lineTo(15.9f, 17.2f)
            lineTo(8.1f, 17.2f)
            lineTo(6.4f, 20.8f)
            lineTo(2.8f, 20.8f)
            close()
        }
    }

    /** ProxyAPI: монограмма P. */
    val ProxyApi: ImageVector by lazy {
        logo("ProxyApi") {
            moveTo(5f, 3.5f)
            lineTo(13.5f, 3.5f)
            arcTo(5f, 5f, 0f, false, true, 13.5f, 13.5f)
            lineTo(8.5f, 13.5f)
            lineTo(8.5f, 20.5f)
            lineTo(5f, 20.5f)
            close()
            // Отверстие буквы: встречное направление обхода.
            moveTo(8.5f, 6f)
            lineTo(8.5f, 11.2f)
            lineTo(13.2f, 11.2f)
            arcTo(2.6f, 2.6f, 0f, false, true, 13.2f, 6f)
            close()
        }
    }

    /** VseGPT: монограмма V. */
    val VseGpt: ImageVector by lazy {
        logo("VseGpt") {
            moveTo(3f, 3.8f)
            lineTo(7.4f, 3.8f)
            lineTo(12f, 16.4f)
            lineTo(16.6f, 3.8f)
            lineTo(21f, 3.8f)
            lineTo(14.1f, 20.2f)
            lineTo(9.9f, 20.2f)
            close()
        }
    }

    /** Together AI: два сцепленных кольца. */
    val Together: ImageVector by lazy {
        logo("Together") {
            ring(8.7f, 12f, 5.6f, 2.9f)
            ring(15.3f, 12f, 5.6f, 2.9f)
        }
    }

    private fun PathBuilder.ring(cx: Float, cy: Float, radius: Float, innerRadius: Float) {
        moveTo(cx - radius, cy)
        arcTo(radius, radius, 0f, true, true, cx + radius, cy)
        arcTo(radius, radius, 0f, true, true, cx - radius, cy)
        close()
        moveTo(cx - innerRadius, cy)
        arcTo(innerRadius, innerRadius, 0f, true, false, cx + innerRadius, cy)
        arcTo(innerRadius, innerRadius, 0f, true, false, cx - innerRadius, cy)
        close()
    }

/** xAI / Grok */
    val Grok: ImageVector by lazy {
        svgLogo("Grok", PathFillType.EvenOdd, "M 9.27 15.29 l 7.978 -5.897 c 0.391 -0.29 0.95 -0.177 1.137 0.272 c 0.98 2.369 0.542 5.215 -1.41 7.169 c -1.951 1.954 -4.667 2.382 -7.149 1.406 l -2.711 1.257 c 3.889 2.661 8.611 2.003 11.562 -0.953 c 2.341 -2.344 3.066 -5.539 2.388 -8.42 l 0.006 0.007 c -0.983 -4.232 0.242 -5.924 2.75 -9.383 c 0.06 -0.082 0.12 -0.164 0.179 -0.248 l -3.301 3.305 v -0.01 L 9.267 15.292 M 7.623 16.723 c -2.792 -2.67 -2.31 -6.801 0.071 -9.184 c 1.761 -1.763 4.647 -2.483 7.166 -1.425 l 2.705 -1.25 a 7.808 7.808 0 0 0 -1.829 -1 A 8.975 8.975 0 0 0 5.984 5.83 c -2.533 2.536 -3.33 6.436 -1.962 9.764 c 1.022 2.487 -0.653 4.246 -2.34 6.022 c -0.599 0.63 -1.199 1.259 -1.682 1.925 l 7.62 -6.815")
    }

    /** NVIDIA / Nemotron */
    val Nvidia: ImageVector by lazy {
        svgLogo("Nvidia", PathFillType.EvenOdd, "M 10.212 8.976 V 7.62 c 0.127 -0.01 0.256 -0.017 0.388 -0.021 c 3.596 -0.117 5.957 3.184 5.957 3.184 s -2.548 3.647 -5.282 3.647 a 3.227 3.227 0 0 1 -1.063 -0.175 v -4.109 c 1.4 0.174 1.681 0.812 2.523 2.258 l 1.873 -1.627 a 4.905 4.905 0 0 0 -3.67 -1.846 a 6.594 6.594 0 0 0 -0.729 0.044 m 0 -4.476 v 2.025 c 0.13 -0.01 0.259 -0.019 0.388 -0.024 c 5.002 -0.174 8.261 4.226 8.261 4.226 s -3.743 4.69 -7.643 4.69 c -0.338 0 -0.675 -0.031 -1.007 -0.092 v 1.25 c 0.278 0.038 0.558 0.057 0.838 0.057 c 3.629 0 6.253 -1.91 8.794 -4.169 c 0.421 0.347 2.146 1.193 2.501 1.564 c -2.416 2.083 -8.048 3.763 -11.24 3.763 c -0.308 0 -0.603 -0.02 -0.894 -0.048 V 19.5 H 24 v -15 H 10.21 z m 0 9.756 v 1.068 c -3.356 -0.616 -4.287 -4.21 -4.287 -4.21 a 7.173 7.173 0 0 1 4.287 -2.138 v 1.172 h -0.005 a 3.182 3.182 0 0 0 -2.502 1.178 s 0.615 2.276 2.507 2.931 m -5.961 -3.3 c 1.436 -1.935 3.604 -3.148 5.961 -3.336 V 6.523 C 5.81 6.887 2 10.723 2 10.723 s 2.158 6.427 8.21 7.015 v -1.166 C 5.77 16 4.25 10.958 4.25 10.958 h -0.002 z")
    }

    /** Z.ai / Zhipu GLM */
    val Zai: ImageVector by lazy {
        svgLogo("Zai", PathFillType.EvenOdd, "M 12.105 2 L 9.927 4.953 H 0.653 L 2.83 2 h 9.276 z M 23.254 19.048 L 21.078 22 h -9.242 l 2.174 -2.952 h 9.244 z M 24 2 L 9.264 22 H 0 L 14.736 2 H 24 z")
    }

    /** Moonshot / Kimi */
    val Kimi: ImageVector by lazy {
        svgLogo(
            "Kimi",
            PathFillType.EvenOdd,
            "M 21.846 0 a 1.923 1.923 0 1 1 0 3.846 H 20.15 a 0.226 0.226 0 0 1 -0.227 -0.226 V 1.923 C 19.923 0.861 20.784 0 21.846 0 z",
            "M 11.065 11.199 l 7.257 -7.2 c 0.137 -0.136 0.06 -0.41 -0.116 -0.41 H 14.3 a 0.164 0.164 0 0 0 -0.117 0.051 l -7.82 7.756 c -0.122 0.12 -0.302 0.013 -0.302 -0.179 V 3.82 c 0 -0.127 -0.083 -0.23 -0.185 -0.23 H 3.186 c -0.103 0 -0.186 0.103 -0.186 0.23 V 19.77 c 0 0.128 0.083 0.23 0.186 0.23 h 2.69 c 0.103 0 0.186 -0.102 0.186 -0.23 v -3.25 c 0 -0.069 0.025 -0.135 0.069 -0.178 l 2.424 -2.406 a 0.158 0.158 0 0 1 0.205 -0.023 l 6.484 4.772 a 7.677 7.677 0 0 0 3.453 1.283 c 0.108 0.012 0.2 -0.095 0.2 -0.23 v -3.06 c 0 -0.117 -0.07 -0.212 -0.164 -0.227 a 5.028 5.028 0 0 1 -2.027 -0.807 l -5.613 -4.064 c -0.117 -0.078 -0.132 -0.279 -0.028 -0.381 z"
        )
    }

    /** MiniMax */
    val MiniMax: ImageVector by lazy {
        svgLogo("MiniMax", PathFillType.EvenOdd, "M 16.278 2 c 1.156 0 2.093 0.927 2.093 2.07 v 12.501 a 0.74 0.74 0 0 0 0.744 0.709 a 0.74 0.74 0 0 0 0.743 -0.709 V 9.099 a 2.06 2.06 0 0 1 2.071 -2.049 A 2.06 2.06 0 0 1 24 9.1 v 6.561 a 0.649 0.649 0 0 1 -0.652 0.645 a 0.649 0.649 0 0 1 -0.653 -0.645 V 9.1 a 0.762 0.762 0 0 0 -0.766 -0.758 a 0.762 0.762 0 0 0 -0.766 0.758 v 7.472 a 2.037 2.037 0 0 1 -2.048 2.026 a 2.037 2.037 0 0 1 -2.048 -2.026 v -12.5 a 0.785 0.785 0 0 0 -0.788 -0.753 a 0.785 0.785 0 0 0 -0.789 0.752 l -0.001 15.904 A 2.037 2.037 0 0 1 13.441 22 a 2.037 2.037 0 0 1 -2.048 -2.026 V 18.04 c 0 -0.356 0.292 -0.645 0.652 -0.645 c 0.36 0 0.652 0.289 0.652 0.645 v 1.934 c 0 0.263 0.142 0.506 0.372 0.638 c 0.23 0.131 0.514 0.131 0.744 0 a 0.734 0.734 0 0 0 0.372 -0.638 V 4.07 c 0 -1.143 0.937 -2.07 2.093 -2.07 z m -5.674 0 c 1.156 0 2.093 0.927 2.093 2.07 v 11.523 a 0.648 0.648 0 0 1 -0.652 0.645 a 0.648 0.648 0 0 1 -0.652 -0.645 V 4.07 a 0.785 0.785 0 0 0 -0.789 -0.78 a 0.785 0.785 0 0 0 -0.789 0.78 v 14.013 a 2.06 2.06 0 0 1 -2.07 2.048 a 2.06 2.06 0 0 1 -2.071 -2.048 V 9.1 a 0.762 0.762 0 0 0 -0.766 -0.758 a 0.762 0.762 0 0 0 -0.766 0.758 v 3.8 a 2.06 2.06 0 0 1 -2.071 2.049 A 2.06 2.06 0 0 1 0 12.9 v -1.378 c 0 -0.357 0.292 -0.646 0.652 -0.646 c 0.36 0 0.653 0.29 0.653 0.646 V 12.9 c 0 0.418 0.343 0.757 0.766 0.757 s 0.766 -0.339 0.766 -0.757 V 9.099 a 2.06 2.06 0 0 1 2.07 -2.048 a 2.06 2.06 0 0 1 2.071 2.048 v 8.984 c 0 0.419 0.343 0.758 0.767 0.758 c 0.423 0 0.766 -0.339 0.766 -0.758 V 4.07 c 0 -1.143 0.937 -2.07 2.093 -2.07 z")
    }

    /** Cohere */
    val Cohere: ImageVector by lazy {
        svgLogo(
            "Cohere",
            PathFillType.EvenOdd,
            "M 8.128 14.099 c 0.592 0 1.77 -0.033 3.398 -0.703 c 1.897 -0.781 5.672 -2.2 8.395 -3.656 c 1.905 -1.018 2.74 -2.366 2.74 -4.18 A 4.56 4.56 0 0 0 18.1 1 H 7.549 A 6.55 6.55 0 0 0 1 7.55 c 0 3.617 2.745 6.549 7.128 6.549 z",
            "M 9.912 18.61 a 4.387 4.387 0 0 1 2.705 -4.052 l 3.323 -1.38 c 3.361 -1.394 7.06 1.076 7.06 4.715 a 5.104 5.104 0 0 1 -5.105 5.104 l -3.597 -0.001 a 4.386 4.386 0 0 1 -4.386 -4.387 z",
            "M 4.776 14.962 A 3.775 3.775 0 0 0 1 18.738 v 0.489 a 3.776 3.776 0 0 0 7.551 0 v -0.49 a 3.775 3.775 0 0 0 -3.775 -3.775 z"
        )
    }

    /** Microsoft / Phi */
    val Microsoft: ImageVector by lazy {
        svgLogo(
            "Microsoft",
            PathFillType.EvenOdd,
            "M 11.49 2 H 2 v 9.492 h 9.492 V 2 h -0.002 z",
            "M 22 2 h -9.492 v 9.492 H 22 V 2 z",
            "M 11.49 12.508 H 2 V 22 h 9.492 v -9.492 h -0.002 z",
            "M 22 12.508 h -9.492 V 22 H 22 v -9.492 z"
        )
    }

    /** Amazon / AWS Nova */
    val Amazon: ImageVector by lazy {
        svgLogo(
            "Amazon",
            PathFillType.EvenOdd,
            "M 6.763 11.212 c 0 0.296 0.032 0.535 0.088 0.71 c 0.064 0.176 0.144 0.368 0.256 0.576 c 0.04 0.063 0.056 0.127 0.056 0.183 c 0 0.08 -0.048 0.16 -0.152 0.24 l -0.503 0.335 a 0.383 0.383 0 0 1 -0.208 0.072 c -0.08 0 -0.16 -0.04 -0.239 -0.112 a 2.47 2.47 0 0 1 -0.287 -0.375 a 6.18 6.18 0 0 1 -0.248 -0.471 c -0.622 0.734 -1.405 1.101 -2.347 1.101 c -0.67 0 -1.205 -0.191 -1.596 -0.574 c -0.39 -0.384 -0.59 -0.894 -0.59 -1.533 c 0 -0.678 0.24 -1.23 0.726 -1.644 c 0.487 -0.415 1.133 -0.623 1.955 -0.623 c 0.272 0 0.551 0.024 0.846 0.064 c 0.296 0.04 0.6 0.104 0.918 0.176 v -0.583 c 0 -0.607 -0.127 -1.03 -0.375 -1.277 c -0.255 -0.248 -0.686 -0.367 -1.3 -0.367 c -0.28 0 -0.568 0.031 -0.863 0.103 c -0.295 0.072 -0.583 0.16 -0.862 0.272 c -0.09 0.04 -0.184 0.075 -0.28 0.104 a 0.488 0.488 0 0 1 -0.127 0.023 c -0.112 0 -0.168 -0.08 -0.168 -0.247 v -0.391 c 0 -0.128 0.016 -0.224 0.056 -0.28 a 0.597 0.597 0 0 1 0.224 -0.167 a 4.577 4.577 0 0 1 1.005 -0.36 a 4.84 4.84 0 0 1 1.246 -0.151 c 0.95 0 1.644 0.216 2.091 0.647 c 0.44 0.43 0.662 1.085 0.662 1.963 v 2.586 h 0.016 z m -3.24 1.214 c 0.263 0 0.534 -0.048 0.822 -0.144 a 1.78 1.78 0 0 0 0.758 -0.51 a 1.27 1.27 0 0 0 0.272 -0.512 c 0.047 -0.191 0.08 -0.423 0.08 -0.694 v -0.335 a 6.66 6.66 0 0 0 -0.735 -0.136 a 6.02 6.02 0 0 0 -0.75 -0.048 c -0.535 0 -0.926 0.104 -1.19 0.32 c -0.263 0.215 -0.39 0.518 -0.39 0.917 c 0 0.375 0.095 0.655 0.295 0.846 c 0.191 0.2 0.47 0.296 0.838 0.296 z m 6.41 0.862 c -0.144 0 -0.24 -0.024 -0.304 -0.08 c -0.064 -0.048 -0.12 -0.16 -0.168 -0.311 L 7.586 6.726 a 1.398 1.398 0 0 1 -0.072 -0.32 c 0 -0.128 0.064 -0.2 0.191 -0.2 h 0.783 c 0.151 0 0.255 0.025 0.31 0.08 c 0.065 0.048 0.113 0.16 0.16 0.312 l 1.342 5.284 l 1.245 -5.284 c 0.04 -0.16 0.088 -0.264 0.151 -0.312 a 0.549 0.549 0 0 1 0.32 -0.08 h 0.638 c 0.152 0 0.256 0.025 0.32 0.08 c 0.063 0.048 0.12 0.16 0.151 0.312 l 1.261 5.348 l 1.381 -5.348 c 0.048 -0.16 0.104 -0.264 0.16 -0.312 a 0.52 0.52 0 0 1 0.311 -0.08 h 0.743 c 0.127 0 0.2 0.065 0.2 0.2 c 0 0.04 -0.009 0.08 -0.017 0.128 a 1.137 1.137 0 0 1 -0.056 0.2 l -1.923 6.17 c -0.048 0.16 -0.104 0.263 -0.168 0.311 a 0.51 0.51 0 0 1 -0.303 0.08 h -0.687 c -0.15 0 -0.255 -0.024 -0.32 -0.08 c -0.063 -0.056 -0.119 -0.16 -0.15 -0.32 L 12.32 7.747 l -1.23 5.14 c -0.04 0.16 -0.087 0.264 -0.15 0.32 c -0.065 0.056 -0.177 0.08 -0.32 0.08 l -0.686 0.001 z m 10.256 0.215 c -0.415 0 -0.83 -0.048 -1.229 -0.143 c -0.399 -0.096 -0.71 -0.2 -0.918 -0.32 c -0.128 -0.071 -0.215 -0.151 -0.247 -0.223 a 0.563 0.563 0 0 1 -0.048 -0.224 v -0.407 c 0 -0.167 0.064 -0.247 0.183 -0.247 c 0.048 0 0.096 0.008 0.144 0.024 c 0.048 0.016 0.12 0.048 0.2 0.08 c 0.271 0.12 0.566 0.215 0.878 0.279 c 0.32 0.064 0.63 0.096 0.95 0.096 c 0.502 0 0.894 -0.088 1.165 -0.264 a 0.86 0.86 0 0 0 0.415 -0.758 a 0.777 0.777 0 0 0 -0.215 -0.559 c -0.144 -0.151 -0.416 -0.287 -0.807 -0.415 l -1.157 -0.36 c -0.583 -0.183 -1.014 -0.454 -1.277 -0.813 a 1.902 1.902 0 0 1 -0.4 -1.158 c 0 -0.335 0.073 -0.63 0.216 -0.886 c 0.144 -0.255 0.335 -0.479 0.575 -0.654 c 0.24 -0.184 0.51 -0.32 0.83 -0.415 c 0.32 -0.096 0.655 -0.136 1.006 -0.136 c 0.175 0 0.36 0.008 0.535 0.032 c 0.183 0.024 0.35 0.056 0.518 0.088 c 0.16 0.04 0.312 0.08 0.455 0.127 c 0.144 0.048 0.256 0.096 0.336 0.144 a 0.69 0.69 0 0 1 0.24 0.2 a 0.43 0.43 0 0 1 0.071 0.263 v 0.375 c 0 0.168 -0.064 0.256 -0.184 0.256 a 0.83 0.83 0 0 1 -0.303 -0.096 a 3.652 3.652 0 0 0 -1.532 -0.311 c -0.455 0 -0.815 0.071 -1.062 0.223 c -0.248 0.152 -0.375 0.383 -0.375 0.71 c 0 0.224 0.08 0.416 0.24 0.567 c 0.16 0.152 0.454 0.304 0.877 0.44 l 1.134 0.358 c 0.574 0.184 0.99 0.44 1.237 0.767 c 0.247 0.327 0.367 0.702 0.367 1.117 c 0 0.343 -0.072 0.655 -0.207 0.926 a 2.157 2.157 0 0 1 -0.583 0.703 c -0.248 0.2 -0.543 0.343 -0.886 0.447 c -0.36 0.111 -0.734 0.167 -1.142 0.167 z",
            "M 0.378 15.475 c 3.384 1.963 7.56 3.153 11.877 3.153 c 2.914 0 6.114 -0.607 9.06 -1.852 c 0.44 -0.2 0.814 0.287 0.383 0.607 c -2.626 1.94 -6.442 2.969 -9.722 2.969 c -4.598 0 -8.74 -1.7 -11.87 -4.526 c -0.247 -0.223 -0.024 -0.527 0.272 -0.351 z m 23.531 -0.2 c 0.287 0.36 -0.08 2.826 -1.485 4.007 c -0.215 0.184 -0.423 0.088 -0.327 -0.151 l 0.175 -0.439 c 0.343 -0.88 0.802 -2.198 0.52 -2.555 c -0.336 -0.43 -2.22 -0.207 -3.074 -0.103 c -0.255 0.032 -0.295 -0.192 -0.063 -0.36 c 1.5 -1.053 3.967 -0.75 4.254 -0.399 z"
        )
    }

    /** ByteDance / Seed / Doubao */
    val ByteDance: ImageVector by lazy {
        svgLogo(
            "ByteDance",
            PathFillType.EvenOdd,
            "M 14.944 18.587 l -1.704 -0.445 V 10.01 l 1.824 -0.462 c 1 -0.254 1.84 -0.461 1.88 -0.453 c 0.032 0 0.056 2.235 0.056 4.972 v 4.973 l -0.176 -0.008 c -0.104 0 -0.952 -0.207 -1.88 -0.446 z",
            "M 7 16.542 c 0 -2.736 0.024 -4.98 0.064 -4.98 c 0.032 -0.008 0.872 0.2 1.88 0.454 l 1.816 0.461 l -0.016 4.05 l -0.024 4.049 l -1.632 0.422 c -0.896 0.23 -1.736 0.445 -1.856 0.469 L 7 21.523 v -4.98 z",
            "M 19.24 12.477 c 0 -9.03 0.008 -9.515 0.144 -9.475 c 0.072 0.024 0.784 0.207 1.576 0.406 c 0.792 0.207 1.576 0.405 1.744 0.445 l 0.296 0.08 l -0.016 8.56 l -0.024 8.568 l -1.624 0.414 c -0.888 0.23 -1.728 0.437 -1.856 0.47 l -0.24 0.055 v -9.523 z",
            "M 1 12.509 c 0 -4.678 0.024 -8.505 0.064 -8.505 c 0.032 0 0.872 0.207 1.872 0.454 l 1.824 0.461 v 7.582 c 0 4.16 -0.016 7.574 -0.032 7.574 c -0.024 0 -0.872 0.215 -1.88 0.47 L 1 21.013 v -8.505 z"
        )
    }

    /** Xiaomi / MiMo */
    val Xiaomi: ImageVector by lazy {
        svgLogo("Xiaomi", PathFillType.NonZero, "M 12 0 C 8.016 0 4.756 0.255 2.493 2.516 C 0.23 4.776 0 8.033 0 12.012 c 0 3.98 0.23 7.235 2.494 9.497 C 4.757 23.77 8.017 24 12 24 c 3.983 0 7.243 -0.23 9.506 -2.491 C 23.77 19.247 24 15.99 24 12.012 c 0 -3.984 -0.233 -7.243 -2.502 -9.504 C 19.234 0.252 15.978 0 12 0 z M 4.906 7.405 h 5.624 c 1.47 0 3.007 0.068 3.764 0.827 c 0.746 0.746 0.827 2.233 0.83 3.676 v 4.54 a 0.15 0.15 0 0 1 -0.152 0.147 h -1.947 a 0.15 0.15 0 0 1 -0.152 -0.148 V 11.83 c -0.002 -0.806 -0.048 -1.634 -0.464 -2.051 c -0.358 -0.36 -1.026 -0.441 -1.72 -0.458 H 7.158 a 0.15 0.15 0 0 0 -0.151 0.147 v 6.98 a 0.15 0.15 0 0 1 -0.152 0.148 H 4.906 a 0.15 0.15 0 0 1 -0.15 -0.148 V 7.554 a 0.15 0.15 0 0 1 0.15 -0.149 z m 12.131 0 h 1.949 a 0.15 0.15 0 0 1 0.15 0.15 v 8.892 a 0.15 0.15 0 0 1 -0.15 0.148 h -1.949 a 0.15 0.15 0 0 1 -0.151 -0.148 V 7.554 a 0.15 0.15 0 0 1 0.151 -0.149 z M 8.92 10.948 h 2.046 c 0.083 0 0.15 0.066 0.15 0.147 v 5.352 a 0.15 0.15 0 0 1 -0.15 0.148 H 8.92 a 0.15 0.15 0 0 1 -0.152 -0.148 v -5.352 a 0.15 0.15 0 0 1 0.152 -0.147 Z")
    }

    /** StepFun */
    val StepFun: ImageVector by lazy {
        svgLogo("StepFun", PathFillType.EvenOdd, "M 12 0 C 5.372 0 0 5.372 0 12 s 5.372 12 12 12 s 12 -5.372 12 -12 S 18.628 0 12 0 z M 8.762 19.614 H 4.376 v -4.386 h 4.386 v 4.386 z m 5.423 0 H 9.798 v -4.386 h 4.387 v 4.386 z m 0 -5.42 H 9.798 V 9.81 h 4.387 v 4.386 z m 0 -5.418 H 9.798 V 4.39 h 4.387 v 4.386 z m 5.422 -0.004 h -4.386 V 4.386 h 4.386 v 4.386 z")
    }

    /** IBM / Granite */
    val IBM: ImageVector by lazy {
        svgLogo("IBM", PathFillType.EvenOdd, "M 24 16.333 V 17 h -3.158 v -0.667 H 24 z m -7.579 0 V 17 h -3.158 v -0.667 h 3.158 z m 2.464 0 L 18.63 17 l -0.25 -0.667 h 0.504 z m -7.075 0 a 2.528 2.528 0 0 1 -1.717 0.667 h -5.04 v -0.667 h 6.757 z m -7.389 0 V 17 H 0 v -0.667 h 4.421 z m 12 -1.333 v 0.667 h -3.158 V 15 h 3.158 z m 2.958 0 l -0.246 0.667 h -1 L 17.885 15 h 1.494 z m -6.937 0 c -0.057 0.237 -0.148 0.46 -0.265 0.667 H 5.053 V 15 h 7.39 z m -8.02 0 v 0.667 H 0 V 15 h 4.421 z M 24 15 v 0.667 h -3.158 V 15 H 24 z m -1.263 -1.333 v 0.666 h -1.895 v -0.666 h 1.895 z m -6.316 0 v 0.666 h -1.895 v -0.666 h 1.895 z m 3.453 0 l -0.248 0.666 h -1.989 l -0.25 -0.666 h 2.487 z m -7.52 0 c 0.056 0.212 0.088 0.435 0.088 0.666 h -2.337 v -0.666 h 2.249 z m -4.143 0 v 0.666 H 6.316 v -0.666 H 8.21 z m -5.053 0 v 0.666 H 1.263 v -0.666 h 1.895 z m 19.579 -1.334 V 13 h -1.895 v -0.667 h 1.895 z m -6.316 0 V 13 h -1.895 v -0.667 h 1.895 z m 3.948 0 l -0.247 0.667 h -2.987 l -0.245 -0.667 h 3.48 z m -8.792 0 c 0.218 0.188 0.405 0.414 0.55 0.667 H 6.315 v -0.667 h 5.26 z m -8.42 0 V 13 H 1.264 v -0.667 h 1.895 z M 18.456 11 l 0.177 0.539 l 0.176 -0.539 h 3.929 v 0.667 h -1.895 v -0.613 l -0.215 0.613 H 16.63 l -0.209 -0.613 v 0.613 h -1.895 V 11 h 3.929 z M 3.158 11 v 0.667 H 1.263 V 11 h 1.895 z m 8.968 0 a 2.555 2.555 0 0 1 -0.55 0.667 h -5.26 V 11 h 5.81 z m 10.61 -1.333 v 0.666 h -3.709 l 0.224 -0.666 h 3.486 z m -4.722 0 l 0.224 0.666 h -3.712 v -0.666 h 3.488 z m -5.572 0 c 0 0.23 -0.032 0.454 -0.088 0.666 h -2.249 v -0.666 h 2.337 z m -4.231 0 v 0.666 H 6.316 v -0.666 H 8.21 z m -5.053 0 v 0.666 H 1.263 v -0.666 h 1.895 z m 14.419 -1.334 l 0.22 0.667 h -4.534 v -0.667 h 4.314 z m 6.423 0 V 9 h -4.536 l 0.229 -0.667 H 24 z m -11.823 0 c 0.117 0.206 0.208 0.43 0.265 0.667 h -7.39 v -0.667 h 7.125 z m -7.756 0 V 9 H 0 v -0.667 h 4.421 z M 17.133 7 l 0.224 0.667 h -4.094 V 7 h 3.87 z M 24 7 v 0.667 h -4.089 L 20.13 7 H 24 z M 10.093 7 c 0.662 0 1.264 0.253 1.717 0.667 H 5.053 V 7 h 5.04 z M 4.42 7 v 0.667 H 0 V 7 h 4.421 z")
    }

    /** Ollama */
    val Ollama: ImageVector by lazy {
        svgLogo("Ollama", PathFillType.EvenOdd, "M 7.905 1.09 c 0.216 0.085 0.411 0.225 0.588 0.41 c 0.295 0.306 0.544 0.744 0.734 1.263 c 0.191 0.522 0.315 1.1 0.362 1.68 a 5.054 5.054 0 0 1 2.049 -0.636 l 0.051 -0.004 c 0.87 -0.07 1.73 0.087 2.48 0.474 c 0.101 0.053 0.2 0.11 0.297 0.17 c 0.05 -0.569 0.172 -1.134 0.36 -1.644 c 0.19 -0.52 0.439 -0.957 0.733 -1.264 a 1.67 1.67 0 0 1 0.589 -0.41 c 0.257 -0.1 0.53 -0.118 0.796 -0.042 c 0.401 0.114 0.745 0.368 1.016 0.737 c 0.248 0.337 0.434 0.769 0.561 1.287 c 0.23 0.934 0.27 2.163 0.115 3.645 l 0.053 0.04 l 0.026 0.019 c 0.757 0.576 1.284 1.397 1.563 2.35 c 0.435 1.487 0.216 3.155 -0.534 4.088 l -0.018 0.021 l 0.002 0.003 c 0.417 0.762 0.67 1.567 0.724 2.4 l 0.002 0.03 c 0.064 1.065 -0.2 2.137 -0.814 3.19 l -0.007 0.01 l 0.01 0.024 c 0.472 1.157 0.62 2.322 0.438 3.486 l -0.006 0.039 a 0.651 0.651 0 0 1 -0.747 0.536 a 0.648 0.648 0 0 1 -0.54 -0.742 c 0.167 -1.033 0.01 -2.069 -0.48 -3.123 a 0.643 0.643 0 0 1 0.04 -0.617 l 0.004 -0.006 c 0.604 -0.924 0.854 -1.83 0.8 -2.72 c -0.046 -0.779 -0.325 -1.544 -0.8 -2.273 a 0.644 0.644 0 0 1 0.18 -0.886 l 0.009 -0.006 c 0.243 -0.159 0.467 -0.565 0.58 -1.12 a 4.229 4.229 0 0 0 -0.095 -1.974 c -0.205 -0.7 -0.58 -1.284 -1.105 -1.683 c -0.595 -0.454 -1.383 -0.673 -2.38 -0.61 a 0.653 0.653 0 0 1 -0.632 -0.371 c -0.314 -0.665 -0.772 -1.141 -1.343 -1.436 a 3.288 3.288 0 0 0 -1.772 -0.332 c -1.245 0.099 -2.343 0.801 -2.67 1.686 a 0.652 0.652 0 0 1 -0.61 0.425 c -1.067 0.002 -1.893 0.252 -2.497 0.703 c -0.522 0.39 -0.878 0.935 -1.066 1.588 a 4.07 4.07 0 0 0 -0.068 1.886 c 0.112 0.558 0.331 1.02 0.582 1.269 l 0.008 0.007 c 0.212 0.207 0.257 0.53 0.109 0.785 c -0.36 0.622 -0.629 1.549 -0.673 2.44 c -0.05 1.018 0.186 1.902 0.719 2.536 l 0.016 0.019 a 0.643 0.643 0 0 1 0.095 0.69 c -0.576 1.236 -0.753 2.252 -0.562 3.052 a 0.652 0.652 0 0 1 -1.269 0.298 c -0.243 -1.018 -0.078 -2.184 0.473 -3.498 l 0.014 -0.035 l -0.008 -0.012 a 4.339 4.339 0 0 1 -0.598 -1.309 l -0.005 -0.019 a 5.764 5.764 0 0 1 -0.177 -1.785 c 0.044 -0.91 0.278 -1.842 0.622 -2.59 l 0.012 -0.026 l -0.002 -0.002 c -0.293 -0.418 -0.51 -0.953 -0.63 -1.545 l -0.005 -0.024 a 5.352 5.352 0 0 1 0.093 -2.49 c 0.262 -0.915 0.777 -1.701 1.536 -2.269 c 0.06 -0.045 0.123 -0.09 0.186 -0.132 c -0.159 -1.493 -0.119 -2.73 0.112 -3.67 c 0.127 -0.518 0.314 -0.95 0.562 -1.287 c 0.27 -0.368 0.614 -0.622 1.015 -0.737 c 0.266 -0.076 0.54 -0.059 0.797 0.042 z m 4.116 9.09 c 0.936 0 1.8 0.313 2.446 0.855 c 0.63 0.527 1.005 1.235 1.005 1.94 c 0 0.888 -0.406 1.58 -1.133 2.022 c -0.62 0.375 -1.451 0.557 -2.403 0.557 c -1.009 0 -1.871 -0.259 -2.493 -0.734 c -0.617 -0.47 -0.963 -1.13 -0.963 -1.845 c 0 -0.707 0.398 -1.417 1.056 -1.946 c 0.668 -0.537 1.55 -0.849 2.485 -0.849 z m 0 0.896 a 3.07 3.07 0 0 0 -1.916 0.65 c -0.461 0.37 -0.722 0.835 -0.722 1.25 c 0 0.428 0.21 0.829 0.61 1.134 c 0.455 0.347 1.124 0.548 1.943 0.548 c 0.799 0 1.473 -0.147 1.932 -0.426 c 0.463 -0.28 0.7 -0.686 0.7 -1.257 c 0 -0.423 -0.246 -0.89 -0.683 -1.256 c -0.484 -0.405 -1.14 -0.643 -1.864 -0.643 z m 0.662 1.21 l 0.004 0.004 c 0.12 0.151 0.095 0.37 -0.056 0.49 l -0.292 0.23 v 0.446 a 0.375 0.375 0 0 1 -0.376 0.373 a 0.375 0.375 0 0 1 -0.376 -0.373 v -0.46 l -0.271 -0.218 a 0.347 0.347 0 0 1 -0.052 -0.49 a 0.353 0.353 0 0 1 0.494 -0.051 l 0.215 0.172 l 0.22 -0.174 a 0.353 0.353 0 0 1 0.49 0.051 z m -5.04 -1.919 c 0.478 0 0.867 0.39 0.867 0.871 a 0.87 0.87 0 0 1 -0.868 0.871 a 0.87 0.87 0 0 1 -0.867 -0.87 a 0.87 0.87 0 0 1 0.867 -0.872 z m 8.706 0 c 0.48 0 0.868 0.39 0.868 0.871 a 0.87 0.87 0 0 1 -0.868 0.871 a 0.87 0.87 0 0 1 -0.867 -0.87 a 0.87 0.87 0 0 1 0.867 -0.872 z M 7.44 2.3 l -0.003 0.002 a 0.659 0.659 0 0 0 -0.285 0.238 l -0.005 0.006 c -0.138 0.189 -0.258 0.467 -0.348 0.832 c -0.17 0.692 -0.216 1.631 -0.124 2.782 c 0.43 -0.128 0.899 -0.208 1.404 -0.237 l 0.01 -0.001 l 0.019 -0.034 c 0.046 -0.082 0.095 -0.161 0.148 -0.239 c 0.123 -0.771 0.022 -1.692 -0.253 -2.444 c -0.134 -0.364 -0.297 -0.65 -0.453 -0.813 a 0.628 0.628 0 0 0 -0.107 -0.09 L 7.44 2.3 z m 9.174 0.04 l -0.002 0.001 a 0.628 0.628 0 0 0 -0.107 0.09 c -0.156 0.163 -0.32 0.45 -0.453 0.814 c -0.29 0.794 -0.387 1.776 -0.23 2.572 l 0.058 0.097 l 0.008 0.014 h 0.03 a 5.184 5.184 0 0 1 1.466 0.212 c 0.086 -1.124 0.038 -2.043 -0.128 -2.722 c -0.09 -0.365 -0.21 -0.643 -0.349 -0.832 l -0.004 -0.006 a 0.659 0.659 0 0 0 -0.285 -0.239 h -0.004 z")
    }

    /** Fireworks AI */
    val Fireworks: ImageVector by lazy {
        svgLogo("Fireworks", PathFillType.EvenOdd, "M 14.8 5 l -2.801 6.795 L 9.195 5 H 7.397 l 3.072 7.428 a 1.64 1.64 0 0 0 3.038 0.002 L 16.598 5 H 14.8 z m 1.196 10.352 l 5.124 -5.244 l -0.699 -1.669 l -5.596 5.739 a 1.664 1.664 0 0 0 -0.343 1.807 a 1.642 1.642 0 0 0 1.516 1.012 L 16 17 l 8 -0.02 l -0.699 -1.669 l -7.303 0.041 h -0.002 z M 2.88 10.104 l 0.699 -1.669 l 5.596 5.739 c 0.468 0.479 0.603 1.189 0.343 1.807 a 1.643 1.643 0 0 1 -1.516 1.012 l -8 -0.018 l -0.002 0.002 l 0.699 -1.669 l 7.303 0.042 l -5.122 -5.246 z")
    }

    /** Cerebras */
    val Cerebras: ImageVector by lazy {
        svgLogo(
            "Cerebras",
            PathFillType.EvenOdd,
            "M 14.121 2.701 a 9.299 9.299 0 0 0 0 18.598 V 22.7 c -5.91 0 -10.7 -4.791 -10.7 -10.701 S 8.21 1.299 14.12 1.299 V 2.7 z m 4.752 3.677 A 7.353 7.353 0 1 0 9.42 17.643 l -0.901 1.074 a 8.754 8.754 0 0 1 -1.08 -12.334 a 8.755 8.755 0 0 1 12.335 -1.08 l -0.901 1.075 z m -2.255 0.844 a 5.407 5.407 0 0 0 -5.048 9.563 l -0.656 1.24 a 6.81 6.81 0 0 1 6.358 -12.043 l -0.654 1.24 z M 14.12 8.539 a 3.46 3.46 0 1 0 0 6.922 v 1.402 a 4.863 4.863 0 0 1 0 -9.726 v 1.402 z",
            "M 15.407 10.836 a 2.24 2.24 0 0 0 -0.51 -0.409 a 1.084 1.084 0 0 0 -0.544 -0.152 c -0.255 0 -0.483 0.047 -0.684 0.14 a 1.58 1.58 0 0 0 -0.84 0.912 c -0.074 0.203 -0.11 0.416 -0.11 0.631 c 0 0.218 0.036 0.43 0.11 0.631 a 1.594 1.594 0 0 0 0.84 0.913 c 0.2 0.093 0.43 0.14 0.684 0.14 c 0.216 0 0.417 -0.046 0.602 -0.135 c 0.188 -0.09 0.35 -0.225 0.475 -0.392 l 0.928 1.006 c -0.14 0.14 -0.3 0.261 -0.482 0.363 a 3.367 3.367 0 0 1 -1.083 0.38 c -0.17 0.026 -0.317 0.04 -0.44 0.04 a 3.315 3.315 0 0 1 -1.182 -0.21 a 2.825 2.825 0 0 1 -0.961 -0.597 a 2.816 2.816 0 0 1 -0.644 -0.929 a 2.987 2.987 0 0 1 -0.238 -1.21 c 0 -0.444 0.08 -0.847 0.238 -1.21 c 0.15 -0.35 0.368 -0.666 0.643 -0.929 c 0.278 -0.261 0.605 -0.464 0.962 -0.596 a 3.315 3.315 0 0 1 1.182 -0.21 c 0.355 0 0.712 0.068 1.072 0.204 c 0.361 0.138 0.685 0.36 0.944 0.649 l -0.962 0.97 z"
        )
    }

    /** DeepInfra */
    val DeepInfra: ImageVector by lazy {
        svgLogo(
            "DeepInfra",
            PathFillType.EvenOdd,
            "M 3.294 7.821 A 2.297 2.297 0 0 1 1 5.527 a 2.297 2.297 0 0 1 2.294 -2.295 A 2.297 2.297 0 0 1 5.59 5.527 A 2.297 2.297 0 0 1 3.294 7.82 z m 0 -3.688 a 1.396 1.396 0 0 0 0 2.79 a 1.396 1.396 0 0 0 0 -2.79 z M 3.294 14.293 A 2.297 2.297 0 0 1 1 11.998 a 2.297 2.297 0 0 1 2.294 -2.294 a 2.297 2.297 0 0 1 2.295 2.294 a 2.297 2.297 0 0 1 -2.295 2.295 z m 0 -3.688 a 1.395 1.395 0 0 0 0 2.788 a 1.395 1.395 0 1 0 0 -2.788 z M 3.294 20.761 A 2.297 2.297 0 0 1 1 18.467 a 2.297 2.297 0 0 1 2.294 -2.295 a 2.297 2.297 0 0 1 2.295 2.295 a 2.297 2.297 0 0 1 -2.295 2.294 z m 0 -3.688 a 1.396 1.396 0 0 0 0 2.79 a 1.396 1.396 0 0 0 0 -2.79 z M 20.738 7.821 a 2.297 2.297 0 0 1 -2.295 -2.294 a 2.297 2.297 0 0 1 2.294 -2.295 a 2.297 2.297 0 0 1 2.295 2.295 a 2.297 2.297 0 0 1 -2.294 2.294 z m 0 -3.688 a 1.396 1.396 0 1 0 1.395 1.395 c 0 -0.77 -0.626 -1.395 -1.395 -1.395 z M 20.738 14.293 a 2.297 2.297 0 0 1 -2.295 -2.295 a 2.297 2.297 0 0 1 2.294 -2.294 a 2.297 2.297 0 0 1 2.295 2.294 a 2.297 2.297 0 0 1 -2.294 2.295 z m 0 -3.688 c -0.769 0 -1.395 0.625 -1.395 1.393 a 1.396 1.396 0 0 0 2.79 0 c 0 -0.77 -0.626 -1.393 -1.395 -1.393 z M 20.738 20.761 a 2.297 2.297 0 0 1 -2.295 -2.294 a 2.297 2.297 0 0 1 2.294 -2.295 a 2.297 2.297 0 0 1 2.295 2.295 a 2.297 2.297 0 0 1 -2.294 2.294 z m 0 -3.688 a 1.396 1.396 0 1 0 1.395 1.395 c 0 -0.77 -0.626 -1.395 -1.395 -1.395 z M 12.016 11.057 a 2.297 2.297 0 0 1 -2.294 -2.294 a 2.297 2.297 0 0 1 2.294 -2.295 a 2.297 2.297 0 0 1 2.295 2.295 a 2.297 2.297 0 0 1 -2.295 2.294 z m 0 -3.688 a 1.396 1.396 0 1 0 1.395 1.395 c 0 -0.77 -0.625 -1.395 -1.395 -1.395 z M 12.017 4.589 a 2.297 2.297 0 0 1 -2.295 -2.295 A 2.297 2.297 0 0 1 12.017 0 a 2.297 2.297 0 0 1 2.294 2.294 a 2.297 2.297 0 0 1 -2.294 2.295 z m 0 -3.688 a 1.396 1.396 0 1 0 1.395 1.395 c 0 -0.77 -0.626 -1.395 -1.395 -1.395 z M 12.017 17.529 a 2.297 2.297 0 0 1 -2.295 -2.295 a 2.297 2.297 0 0 1 2.295 -2.294 a 2.297 2.297 0 0 1 2.294 2.294 a 2.297 2.297 0 0 1 -2.294 2.295 z m 0 -3.688 a 1.396 1.396 0 1 0 1.395 1.395 c 0 -0.77 -0.626 -1.395 -1.395 -1.395 z M 12.016 24 a 2.297 2.297 0 0 1 -2.294 -2.295 a 2.297 2.297 0 0 1 2.294 -2.294 a 2.297 2.297 0 0 1 2.295 2.294 A 2.297 2.297 0 0 1 12.016 24 z m 0 -3.688 a 1.396 1.396 0 1 0 1.395 1.395 c 0 -0.77 -0.625 -1.395 -1.395 -1.395 z",
            "M 8.363 8.222 a 0.742 0.742 0 0 1 -0.277 -0.053 l -1.494 -0.596 a 0.75 0.75 0 1 1 0.557 -1.392 l 1.493 0.595 a 0.75 0.75 0 0 1 -0.278 1.446 h -0.001 z M 8.363 14.566 a 0.743 0.743 0 0 1 -0.277 -0.053 l -1.494 -0.595 a 0.75 0.75 0 1 1 0.557 -1.393 l 1.493 0.596 a 0.75 0.75 0 0 1 -0.278 1.445 h -0.001 z M 17.124 11.397 a 0.741 0.741 0 0 1 -0.277 -0.054 l -1.493 -0.595 a 0.75 0.75 0 1 1 0.555 -1.392 l 1.493 0.595 a 0.75 0.75 0 0 1 -0.278 1.446 z M 17.124 5.05 a 0.744 0.744 0 0 1 -0.277 -0.054 L 15.354 4.4 a 0.75 0.75 0 0 1 0.555 -1.392 l 1.493 0.596 a 0.75 0.75 0 0 1 -0.278 1.445 z M 17.124 17.739 a 0.743 0.743 0 0 1 -0.277 -0.053 l -1.494 -0.596 a 0.75 0.75 0 1 1 0.556 -1.392 l 1.493 0.596 a 0.75 0.75 0 0 1 -0.278 1.445 z M 6.91 17.966 a 0.75 0.75 0 0 1 -0.279 -1.445 l 1.494 -0.595 a 0.749 0.749 0 1 1 0.556 1.392 l -1.493 0.595 a 0.743 0.743 0 0 1 -0.277 0.053 H 6.91 z M 6.91 11.66 a 0.75 0.75 0 0 1 -0.279 -1.446 l 1.494 -0.595 a 0.75 0.75 0 0 1 0.556 1.392 l -1.493 0.595 a 0.743 0.743 0 0 1 -0.277 0.053 H 6.91 z M 6.91 5.033 a 0.75 0.75 0 0 1 -0.279 -1.446 l 1.494 -0.595 a 0.75 0.75 0 0 1 0.556 1.392 l -1.493 0.596 a 0.744 0.744 0 0 1 -0.277 0.053 H 6.91 z M 8.363 21.364 a 0.743 0.743 0 0 1 -0.277 -0.053 l -1.494 -0.596 a 0.75 0.75 0 0 1 0.555 -1.392 l 1.494 0.595 a 0.75 0.75 0 0 1 -0.278 1.446 z M 15.63 8.223 a 0.75 0.75 0 0 1 -0.278 -1.447 l 1.494 -0.595 a 0.75 0.75 0 0 1 0.556 1.393 l -1.494 0.595 a 0.744 0.744 0 0 1 -0.276 0.054 h -0.002 z M 15.63 14.567 a 0.75 0.75 0 0 1 -0.278 -1.446 l 1.494 -0.596 a 0.75 0.75 0 0 1 0.556 1.394 l -1.494 0.595 a 0.743 0.743 0 0 1 -0.276 0.053 h -0.002 z M 15.63 21.363 a 0.749 0.749 0 0 1 -0.278 -1.445 l 1.494 -0.595 a 0.75 0.75 0 1 1 0.555 1.392 l -1.494 0.595 a 0.741 0.741 0 0 1 -0.277 0.053 z"
        )
    }

    /** Hugging Face */
    val HuggingFace: ImageVector by lazy {
        svgLogo("HuggingFace", PathFillType.EvenOdd, "M 16.781 3.277 c 2.997 1.704 4.844 4.851 4.844 8.258 c 0 0.995 -0.155 1.955 -0.443 2.857 a 1.332 1.332 0 0 1 1.125 0.4 a 1.41 1.41 0 0 1 0.2 1.723 c 0.204 0.165 0.352 0.385 0.428 0.632 l 0.017 0.062 c 0.06 0.222 0.12 0.69 -0.2 1.166 c 0.244 0.37 0.279 0.836 0.093 1.236 c -0.255 0.57 -0.893 1.018 -2.128 1.5 l -0.202 0.078 l -0.131 0.048 c -0.478 0.173 -0.89 0.295 -1.061 0.345 l -0.086 0.024 c -0.89 0.243 -1.808 0.375 -2.732 0.394 c -1.32 0 -2.3 -0.36 -2.923 -1.067 a 9.852 9.852 0 0 1 -3.18 0.018 C 9.778 21.647 8.802 22 7.494 22 a 11.249 11.249 0 0 1 -2.541 -0.343 l -0.221 -0.06 l -0.273 -0.08 a 16.574 16.574 0 0 1 -1.175 -0.405 c -1.237 -0.483 -1.875 -0.93 -2.13 -1.501 c -0.186 -0.4 -0.151 -0.867 0.093 -1.236 a 1.42 1.42 0 0 1 -0.2 -1.166 c 0.069 -0.273 0.226 -0.516 0.447 -0.694 a 1.41 1.41 0 0 1 0.2 -1.722 c 0.233 -0.248 0.557 -0.391 0.917 -0.407 l 0.078 -0.001 a 9.385 9.385 0 0 1 -0.44 -2.85 c 0 -3.407 1.847 -6.554 4.844 -8.258 a 9.822 9.822 0 0 1 9.687 0 z M 4.188 14.758 c 0.125 0.687 2.357 2.35 2.14 2.707 c -0.19 0.315 -0.796 -0.239 -0.948 -0.386 l -0.041 -0.04 l -0.168 -0.147 c -0.561 -0.479 -2.304 -1.9 -2.74 -1.432 c -0.43 0.46 0.119 0.859 1.055 1.42 l 0.784 0.467 l 0.136 0.083 c 1.045 0.643 1.12 0.84 0.95 1.113 c -0.188 0.295 -3.07 -2.1 -3.34 -1.083 c -0.27 1.011 2.942 1.304 2.744 2.006 c -0.2 0.7 -2.265 -1.324 -2.685 -0.537 c -0.425 0.79 2.913 1.718 2.94 1.725 l 0.16 0.04 l 0.175 0.042 c 1.227 0.284 3.565 0.65 4.435 -0.604 c 0.673 -0.973 0.64 -1.709 -0.248 -2.61 l -0.057 -0.057 c -0.945 -0.928 -1.495 -2.288 -1.495 -2.288 l -0.017 -0.058 l -0.025 -0.072 c -0.082 -0.22 -0.284 -0.639 -0.63 -0.584 c -0.46 0.073 -0.798 1.21 0.12 1.933 l 0.05 0.038 c 0.977 0.721 -0.195 1.21 -0.573 0.534 l -0.058 -0.104 l -0.143 -0.25 c -0.463 -0.799 -1.282 -2.111 -1.739 -2.397 c -0.532 -0.332 -0.907 -0.148 -0.782 0.541 z m 14.842 -0.541 c -0.533 0.335 -1.563 2.074 -1.94 2.751 a 0.613 0.613 0 0 1 -0.687 0.302 a 0.436 0.436 0 0 1 -0.176 -0.098 a 0.303 0.303 0 0 1 -0.049 -0.06 l -0.014 -0.028 l -0.008 -0.02 l -0.007 -0.019 l -0.003 -0.013 l -0.003 -0.017 a 0.289 0.289 0 0 1 -0.004 -0.048 c 0 -0.12 0.071 -0.266 0.25 -0.427 c 0.026 -0.024 0.054 -0.047 0.084 -0.07 l 0.047 -0.036 c 0.022 -0.016 0.043 -0.032 0.063 -0.049 c 0.883 -0.71 0.573 -1.81 0.131 -1.917 l -0.031 -0.006 l -0.056 -0.004 a 0.368 0.368 0 0 0 -0.062 0.006 l -0.028 0.005 l -0.042 0.014 l -0.039 0.017 l -0.028 0.015 l -0.028 0.019 l -0.036 0.027 l -0.023 0.02 c -0.173 0.158 -0.273 0.428 -0.31 0.542 l -0.016 0.054 s -0.53 1.309 -1.439 2.234 l -0.054 0.054 c -0.365 0.358 -0.596 0.69 -0.702 1.018 c -0.143 0.437 -0.066 0.868 0.21 1.353 c 0.055 0.097 0.117 0.195 0.187 0.296 c 0.882 1.275 3.282 0.876 4.494 0.59 l 0.286 -0.07 l 0.25 -0.074 c 0.276 -0.084 0.736 -0.233 1.2 -0.42 l 0.188 -0.077 l 0.065 -0.028 l 0.064 -0.028 l 0.124 -0.056 l 0.081 -0.038 c 0.529 -0.252 0.964 -0.543 0.994 -0.827 l 0.001 -0.036 a 0.299 0.299 0 0 0 -0.037 -0.139 c -0.094 -0.176 -0.271 -0.212 -0.491 -0.168 l -0.045 0.01 c -0.044 0.01 -0.09 0.024 -0.136 0.04 l -0.097 0.035 l -0.054 0.022 c -0.559 0.23 -1.238 0.705 -1.607 0.745 h 0.006 a 0.452 0.452 0 0 1 -0.05 0.003 h -0.024 l -0.024 -0.003 l -0.023 -0.005 c -0.068 -0.016 -0.116 -0.06 -0.14 -0.142 a 0.22 0.22 0 0 1 -0.005 -0.1 c 0.062 -0.345 0.958 -0.595 1.713 -0.91 l 0.066 -0.028 c 0.528 -0.224 0.97 -0.483 0.985 -0.832 v -0.04 a 0.47 0.47 0 0 0 -0.016 -0.098 c -0.048 -0.18 -0.175 -0.251 -0.36 -0.251 c -0.785 0 -2.55 1.36 -2.92 1.36 c -0.025 0 -0.048 -0.007 -0.058 -0.024 a 0.6 0.6 0 0 1 -0.046 -0.088 c -0.1 -0.238 0.068 -0.462 1.06 -1.066 l 0.209 -0.126 c 0.538 -0.32 1.01 -0.588 1.341 -0.831 c 0.29 -0.212 0.475 -0.406 0.503 -0.6 l 0.003 -0.028 c 0.008 -0.113 -0.038 -0.227 -0.147 -0.344 a 0.266 0.266 0 0 0 -0.07 -0.054 l -0.034 -0.015 l -0.013 -0.005 a 0.403 0.403 0 0 0 -0.13 -0.02 c -0.162 0 -0.369 0.07 -0.595 0.18 c -0.637 0.313 -1.431 0.952 -1.826 1.285 l -0.249 0.215 l -0.033 0.033 c -0.08 0.078 -0.288 0.27 -0.493 0.386 l -0.071 0.037 l -0.041 0.019 a 0.535 0.535 0 0 1 -0.122 0.036 h 0.005 a 0.346 0.346 0 0 1 -0.031 0.003 l 0.01 -0.001 l -0.013 0.001 c -0.079 0.005 -0.145 -0.021 -0.19 -0.095 a 0.113 0.113 0 0 1 -0.014 -0.065 c 0.027 -0.465 2.034 -1.991 2.152 -2.642 l 0.009 -0.048 c 0.1 -0.65 -0.271 -0.817 -0.791 -0.493 z M 11.938 2.984 c -4.798 0 -8.688 3.829 -8.688 8.55 c 0 0.692 0.083 1.364 0.24 2.008 l 0.008 -0.009 c 0.252 -0.298 0.612 -0.46 1.017 -0.46 c 0.355 0.008 0.699 0.117 0.993 0.312 c 0.22 0.14 0.465 0.384 0.715 0.694 c 0.261 -0.372 0.69 -0.598 1.15 -0.605 c 0.852 0 1.367 0.728 1.562 1.383 l 0.047 0.105 l 0.06 0.127 c 0.192 0.396 0.595 1.139 1.143 1.68 c 1.06 1.04 1.324 2.115 0.8 3.266 a 8.865 8.865 0 0 0 2.024 -0.014 c -0.505 -1.12 -0.26 -2.17 0.74 -3.186 l 0.066 -0.066 c 0.695 -0.684 1.157 -1.69 1.252 -1.912 c 0.195 -0.655 0.708 -1.383 1.56 -1.383 c 0.46 0.007 0.889 0.233 1.15 0.605 c 0.25 -0.31 0.495 -0.553 0.718 -0.694 a 1.87 1.87 0 0 1 0.99 -0.312 c 0.357 0 0.682 0.126 0.925 0.36 c 0.14 -0.61 0.215 -1.245 0.215 -1.898 c 0 -4.722 -3.89 -8.55 -8.687 -8.55 z m 1.857 8.926 l 0.439 -0.212 c 0.553 -0.264 0.89 -0.383 0.89 0.152 c 0 1.093 -0.771 3.208 -3.155 3.262 h -0.184 c -2.325 -0.052 -3.116 -2.06 -3.156 -3.175 l -0.001 -0.087 c 0 -1.107 1.452 0.586 3.25 0.586 c 0.716 0 1.379 -0.272 1.917 -0.526 z m 4.017 -3.143 c 0.45 0 0.813 0.358 0.813 0.8 c 0 0.441 -0.364 0.8 -0.813 0.8 a 0.806 0.806 0 0 1 -0.812 -0.8 c 0 -0.442 0.364 -0.8 0.812 -0.8 z m -11.624 0 c 0.448 0 0.812 0.358 0.812 0.8 c 0 0.441 -0.364 0.8 -0.812 0.8 a 0.806 0.806 0 0 1 -0.813 -0.8 c 0 -0.442 0.364 -0.8 0.813 -0.8 z m 7.79 -0.841 c 0.32 -0.384 0.846 -0.54 1.33 -0.394 c 0.483 0.146 0.83 0.564 0.878 1.06 c 0.048 0.495 -0.212 0.97 -0.659 1.203 c -0.322 0.168 -0.447 -0.477 -0.767 -0.585 l 0.002 -0.003 c -0.287 -0.098 -0.772 0.362 -0.925 0.079 a 1.215 1.215 0 0 1 0.14 -1.36 z m -4.323 0 c 0.322 0.384 0.377 0.92 0.14 1.36 c -0.152 0.283 -0.64 -0.177 -0.925 -0.079 l 0.003 0.003 c -0.108 0.036 -0.194 0.134 -0.273 0.24 l -0.118 0.165 c -0.11 0.15 -0.22 0.262 -0.377 0.18 a 1.226 1.226 0 0 1 -0.658 -1.204 c 0.048 -0.495 0.395 -0.913 0.878 -1.059 a 1.262 1.262 0 0 1 1.33 0.394 z")
    }

    private val OPENAI_O_SERIES_REGEX = Regex("""(?:^|[/._-])o[134](?:$|[-._/])""")
    private val GLM_MODEL_REGEX = Regex("""(?:^|[\s/._-])glm(?:$|[\s\-._/\d])""")
    private val PHI_MODEL_REGEX = Regex("""(?:^|[/._-])phi(?:$|[-._/\d])""")
    private val NOVA_MODEL_REGEX = Regex("""(?:^|[/._-])nova(?:[-._](?:micro|lite|pro|premier|sonic|canvas|reel|\d))""")
    private val MIMO_MODEL_REGEX = Regex("""(?:^|[\s/._-])mimo(?:$|[\s\-._/\d])""")
    private val STEP_MODEL_REGEX = Regex("""(?:^|[/._-])step(?:-[1234]|[\d])""")
    private val IBM_KEY_REGEX = Regex("""(?:^|[\s/._-])ibm(?:$|[\s/._-])""")
    private val ZAI_KEY_REGEX = Regex("""(?:^|[\s/._-])z[.-]?ai(?:$|[\s/._-])""")
    private val AWS_KEY_REGEX = Regex("""(?:^|[\s/._-])aws(?:$|[\s/._-])""")
    private val MONOGRAM_SPLIT_REGEX = Regex("""[^A-Za-zА-Яа-яЁё0-9]+""")
    private val MONOGRAM_STOP_TOKENS = setOf(
        "ai", "api", "org", "io", "com", "net", "dev", "v1", "v2", "v3",
        "models", "model", "labs", "lab", "www", "router", "inference", "cloud"
    )

    /**
     * Подбор логотипа по названию модели и провайдера.
     *
     * Сначала проверяется ID модели (актуально для хабов вроде OpenRouter или Groq,
     * где через один эндпоинт запускаются модели Anthropic, Meta, DeepSeek, Google и т.д.).
     * Если по модели семейство не определено — берётся логотип провайдера (в том числе по host baseUrl).
     */
    fun forModelOrProvider(
        modelId: String,
        providerId: String = "",
        providerName: String = "",
        baseUrl: String = ""
    ): ImageVector? {
        val modelKey = modelId.trim().removePrefix("models/").lowercase()
        return when {
            // Хаб-алиасы OpenRouter (openrouter/auto, openrouter/free и т.д.)
            modelKey.startsWith("openrouter/") -> OpenRouter
            // Модели семейств (порядок важен для составных ID вроде nvidia/llama-3.1-nemotron или deepseek-r1-distill-qwen)
            modelKey.contains("gemini") || modelKey.contains("gemma") || modelKey.startsWith("google/") -> Gemini
            modelKey.contains("gpt") || modelKey.contains("chatgpt") || modelKey.contains("openai") ||
                OPENAI_O_SERIES_REGEX.containsMatchIn(modelKey) -> OpenAI
            modelKey.contains("claude") || modelKey.contains("anthropic") -> Claude
            modelKey.contains("deepseek") -> DeepSeek
            modelKey.contains("grok") || modelKey.startsWith("x-ai/") || modelKey.startsWith("xai/") -> Grok
            modelKey.contains("nvidia") || modelKey.contains("nemotron") -> Nvidia
            modelKey.contains("zai-org") || modelKey.startsWith("z-ai/") || modelKey.contains("zhipu") ||
                modelKey.contains("thudm") || modelKey.contains("chatglm") ||
                GLM_MODEL_REGEX.containsMatchIn(modelKey) -> Zai
            modelKey.contains("kimi") || modelKey.contains("moonshot") -> Kimi
            modelKey.contains("minimax") || modelKey.contains("abab") -> MiniMax
            modelKey.contains("cohere") || modelKey.contains("command-r") ||
                modelKey.contains("command-a") || modelKey.contains("c4ai-command") -> Cohere
            modelKey.contains("microsoft") || PHI_MODEL_REGEX.containsMatchIn(modelKey) -> Microsoft
            modelKey.contains("amazon") || NOVA_MODEL_REGEX.containsMatchIn(modelKey) -> Amazon
            modelKey.contains("bytedance") || modelKey.contains("doubao") ||
                modelKey.startsWith("seed-") -> ByteDance
            modelKey.contains("xiaomi") || MIMO_MODEL_REGEX.containsMatchIn(modelKey) -> Xiaomi
            modelKey.contains("stepfun") || STEP_MODEL_REGEX.containsMatchIn(modelKey) -> StepFun
            modelKey.contains("granite") || modelKey.startsWith("ibm/") || modelKey.startsWith("ibm-") -> IBM
            modelKey.contains("qwen") || modelKey.contains("qwq") || modelKey.contains("qvq") -> Qwen
            modelKey.contains("mistral") || modelKey.contains("mixtral") ||
                modelKey.contains("codestral") || modelKey.contains("pixtral") ||
                modelKey.contains("ministral") || modelKey.contains("devstral") ||
                modelKey.contains("magistral") -> Mistral
            modelKey.contains("sonar") || modelKey.contains("perplexity") -> Perplexity
            modelKey.contains("llama") || modelKey.startsWith("meta/") || modelKey.startsWith("meta-") -> Meta
            modelKey.contains("groq") -> Groq
            // Если по модели не определено — смотрим провайдера (включая домен из baseUrl)
            else -> forProvider(providerId, providerName, baseUrl)
        }
    }

    /**
     * Логотип по провайдеру (по ID, имени или домену из baseUrl).
     */
    fun forProvider(
        providerId: String,
        providerName: String = "",
        baseUrl: String = ""
    ): ImageVector? {
        val host = extractHost(baseUrl)
        val key = "$providerId $providerName $host".lowercase()
        return when {
            key.contains("ollama") || baseUrl.contains(":11434") -> Ollama
            key.contains("openai") || key.contains("chatgpt") -> OpenAI
            key.contains("anthropic") || key.contains("claude") -> Claude
            key.contains("gemini") || key.contains("google") -> Gemini
            key.contains("deepseek") -> DeepSeek
            key.contains("grok") || key.contains("x-ai") || key.contains("xai") ||
                host == "x.ai" || host.endsWith(".x.ai") -> Grok
            key.contains("groq") -> Groq
            key.contains("openrouter") -> OpenRouter
            key.contains("mistral") -> Mistral
            key.contains("nvidia") || key.contains("nemotron") -> Nvidia
            key.contains("zhipu") || key.contains("bigmodel") || key.contains("chatglm") ||
                host == "z.ai" || host.endsWith(".z.ai") ||
                ZAI_KEY_REGEX.containsMatchIn(key) || GLM_MODEL_REGEX.containsMatchIn(key) -> Zai
            key.contains("kimi") || key.contains("moonshot") -> Kimi
            key.contains("minimax") || key.contains("minimaxi") -> MiniMax
            key.contains("cohere") -> Cohere
            key.contains("microsoft") || key.contains("azure") -> Microsoft
            key.contains("amazon") || key.contains("bedrock") ||
                host.endsWith(".amazonaws.com") || AWS_KEY_REGEX.containsMatchIn(key) -> Amazon
            key.contains("bytedance") || key.contains("doubao") || key.contains("volces") -> ByteDance
            key.contains("xiaomi") || MIMO_MODEL_REGEX.containsMatchIn(key) -> Xiaomi
            key.contains("stepfun") -> StepFun
            key.contains("granite") || key.contains("watsonx") || IBM_KEY_REGEX.containsMatchIn(key) -> IBM
            key.contains("fireworks") -> Fireworks
            key.contains("cerebras") -> Cerebras
            key.contains("deepinfra") -> DeepInfra
            key.contains("huggingface") || key.contains("hugging face") ||
                host == "hf.co" || host.endsWith(".hf.co") -> HuggingFace
            key.contains("meta") || key.contains("llama") -> Meta
            key.contains("perplexity") -> Perplexity
            key.contains("qwen") || key.contains("alibaba") || key.contains("dashscope") -> Qwen
            key.contains("token harbor") || key.contains("tokenharbor") -> TokenHarbor
            key.contains("clean api") || key.contains("cleanapi") -> CleanApi
            key.contains("abnibots") -> AbniBots
            key.contains("proxyapi") || key.contains("proxy api") -> ProxyApi
            key.contains("vsegpt") || key.contains("vse gpt") -> VseGpt
            key.contains("together") -> Together
            else -> null
        }
    }

    /**
     * Аккуратная 1–2 символьная монограмма для редких вендоров и кастомных провайдеров,
     * у которых нет отдельного векторного логотипа (вместо одинакового робота).
     */
    fun monogramFor(
        modelId: String = "",
        providerName: String = "",
        baseUrl: String = ""
    ): String {
        val cleanModel = modelId.trim().removePrefix("models/")
        val candidate = when {
            cleanModel.contains('/') -> {
                val segments = cleanModel.split('/').filter { it.isNotBlank() }
                if (segments.size >= 2 && segments[0].equals("accounts", ignoreCase = true)) {
                    segments[1]
                } else {
                    segments.firstOrNull().orEmpty()
                }
            }
            cleanModel.isNotBlank() && !cleanModel.equals("модель не выбрана", ignoreCase = true) -> cleanModel
            providerName.isNotBlank() && !providerName.equals("провайдер", ignoreCase = true) -> providerName
            else -> meaningfulHostLabel(extractHost(baseUrl))
        }
        return extractMonogram(candidate)
            ?: extractMonogram(providerName.takeUnless { it.equals("провайдер", ignoreCase = true) }.orEmpty())
            ?: extractMonogram(meaningfulHostLabel(extractHost(baseUrl)))
            ?: "AI"
    }

    private fun meaningfulHostLabel(host: String): String {
        if (host.isBlank()) return ""
        val labels = host.split('.').filter { it.isNotBlank() }
        if (labels.isEmpty()) return ""
        // Отбрасываем TLD и технические поддомены вроде api / www / router
        val withoutTld = if (labels.size >= 2) labels.dropLast(1) else labels
        return withoutTld.lastOrNull { it.lowercase() !in MONOGRAM_STOP_TOKENS }
            ?: withoutTld.lastOrNull()
            ?: labels.first()
    }

    private fun extractMonogram(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val allTokens = MONOGRAM_SPLIT_REGEX.split(trimmed).filter { it.isNotBlank() }
        if (allTokens.isEmpty()) return null
        val meaningful = allTokens.filter { it.lowercase() !in MONOGRAM_STOP_TOKENS }.ifEmpty { allTokens }
        return if (meaningful.size >= 2) {
            "${meaningful[0].first()}${meaningful[1].first()}".uppercase()
        } else {
            val token = meaningful[0]
            token.take(2).uppercase()
        }
    }

    /** Совместимость со старыми именами */
    val OpenAi: ImageVector get() = OpenAI
    val Anthropic: ImageVector get() = Claude
}
