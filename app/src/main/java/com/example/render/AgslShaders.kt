package com.example.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * Hardware-accelerated AGSL shaders (Android 13+ / API 33+) for:
 * 1) Nebula Plasma generator
 * 2) MilkDrop Per-Pixel Feedback Warp Engine (zoom, zoomexp, rot, sx/sy, dx/dy, warp, pinwheel, decay)
 * 3) Composite Post-FX Pipeline (WaveFold, Vortex, Ripple, Chromatic Split, CRT, Quad Mirror, Polar Unwrap, Solarize, Posterize)
 */
object AgslShaders {

    private const val NEBULA_PLASMA_SRC = """
        uniform float2 iResolution;
        uniform float iTime;
        uniform float iTurbulence;
        uniform float iWarpFlow;
        uniform float iGlowDensity;
        uniform float iBass;
        uniform float iTreble;
        uniform float4 iColorA;
        uniform float4 iColorB;
        uniform float4 iColorC;
        uniform float iOpacity;

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / min(iResolution.x, iResolution.y);
            float t = iTime * (0.45 + iWarpFlow * 0.55);
            float r = length(uv);
            float a = atan(uv.y, uv.x);

            float v1 = sin(uv.x * (2.5 + iTurbulence) + t + cos(uv.y * iTurbulence - t * 0.7));
            float v2 = cos(uv.y * (2.2 + iTurbulence * 1.1) - t * 0.8 + sin(r * 5.0 - iBass * 4.0));
            float v3 = sin((uv.x + uv.y) * iTurbulence + a * 2.0 + t * 0.6 + iTreble * 3.0);
            float plasma = (v1 + v2 + v3) / 3.0;
            float ridge = 1.0 - abs(plasma);
            float glow = pow(clamp(ridge, 0.0, 1.0), 2.2 - clamp(iGlowDensity * 0.55, 0.0, 1.4));

            float m1 = smoothstep(-0.7, 0.7, plasma);
            float m2 = smoothstep(-0.4, 0.9, v2);
            float3 col = mix(iColorA.rgb, iColorB.rgb, m1);
            col = mix(col, iColorC.rgb, m2 * 0.65);
            col *= (0.35 + glow * (0.85 + iBass * 0.75) * iGlowDensity);

            float vignette = smoothstep(1.35, 0.15, r);
            float alpha = clamp((0.25 + glow * 0.85) * vignette * iOpacity, 0.0, 1.0);
            return half4(col * alpha, alpha);
        }
    """

    private const val FEEDBACK_WARP_SRC = """
        uniform shader prevFrame;
        uniform float2 iResolution;
        uniform float iTime;
        uniform float iDecay;
        uniform float iEchoZoom;
        uniform float iEchoRotRad;
        uniform float iMilkWarpAmt;
        uniform float iMilkWarpScale;
        uniform float iMilkWarpSpeed;
        uniform float iTunnelZoom;
        uniform float iTunnelZoomExp;
        uniform float iTunnelPulse;
        uniform float iStretchX;
        uniform float iStretchY;
        uniform float iPanDx;
        uniform float iPanDy;
        uniform float iPinwheelRot;
        uniform float iPinwheelRadFreq;
        uniform float iPinwheelPetals;

        half4 main(float2 fragCoord) {
            float2 res = max(iResolution, float2(1.0, 1.0));
            float minDim = min(res.x, res.y);
            float2 center = 0.5 * res;
            float2 uv = (fragCoord - center) / minDim;

            float rad = length(uv);
            float ang = atan(uv.y, uv.x);

            // MilkDrop zoom + zoomexp + echo zoom
            float zoomExpFactor = pow(max(rad, 0.001), iTunnelZoomExp);
            float effZoom = iEchoZoom * (1.0 + (iTunnelZoom - 1.0) * zoomExpFactor + iTunnelPulse * (1.0 - rad));
            uv /= max(effZoom, 0.35);

            // Anamorphic sx / sy + dx / dy
            uv = float2(uv.x / max(iStretchX, 0.3), uv.y / max(iStretchY, 0.3));
            uv -= float2(iPanDx, iPanDy);

            // MilkDrop rot + angular pinwheel
            float pinAngle = iEchoRotRad + iPinwheelRot * 0.08 * sin(rad * iPinwheelRadFreq - iTime * 1.8)
                           + iPinwheelRot * 0.04 * cos(ang * floor(iPinwheelPetals + 0.5));
            float cs = cos(pinAngle);
            float sn = sin(pinAngle);
            uv = float2(uv.x * cs - uv.y * sn, uv.x * sn + uv.y * cs);

            // MilkDrop per-pixel sinusoidal liquid warp
            if (iMilkWarpAmt > 0.001) {
                float wt = iTime * iMilkWarpSpeed * 1.4;
                float wx = sin(uv.y * iMilkWarpScale * 3.14159 + wt) + cos((uv.x + uv.y) * iMilkWarpScale * 2.0 - wt * 0.7);
                float wy = cos(uv.x * iMilkWarpScale * 3.14159 - wt * 0.9) + sin((uv.x - uv.y) * iMilkWarpScale * 2.0 + wt * 0.6);
                uv += float2(wx, wy) * (iMilkWarpAmt * 0.012);
            }

            float2 sampleCoord = uv * minDim + center;
            half4 prev = prevFrame.eval(sampleCoord);
            return half4(prev.rgb * iDecay, prev.a * iDecay);
        }
    """

    private const val COMPOSITE_FX_SRC = """
        uniform shader background;
        uniform float2 iResolution;
        uniform float iTime;
        uniform float iWaveFoldAmp;
        uniform float iWaveFoldFreq;
        uniform float iWaveFoldSpeed;
        uniform float iVortexTwist;
        uniform float iVortexRadius;
        uniform float iVortexOrbit;
        uniform float iRippleAmp;
        uniform float iRippleFreq;
        uniform float iRippleSpeed;
        uniform float iChromaticPx;
        uniform float iChromaticAngleRad;
        uniform float iChromaticSpread;
        uniform float iCrtDepth;
        uniform float iCrtSpacing;
        uniform float iCrtBarrel;
        uniform float iShakeX;
        uniform float iShakeY;
        uniform float iMilkWarpAmt;
        uniform float iMilkWarpScale;
        uniform float iMilkWarpSpeed;
        uniform float iTunnelZoom;
        uniform float iTunnelZoomExp;
        uniform float iTunnelPulse;
        uniform float iStretchX;
        uniform float iStretchY;
        uniform float iPinwheelRot;
        uniform float iPinwheelRadFreq;
        uniform float iPinwheelPetals;
        uniform float iQuadMirrorBlend;
        uniform float iQuadOffset;
        uniform float iQuadInvert;
        uniform float iPolarBlend;
        uniform float iPolarArms;
        uniform float iPolarSpin;
        uniform float iSolarizeMix;
        uniform float iSolarizeFreq;
        uniform float iSolarizePhase;
        uniform float iPosterizeMix;
        uniform float iPosterizeLevels;
        uniform float iPosterizeEdge;

        float2 applyBarrel(float2 uv, float k) {
            float r2 = dot(uv, uv);
            return uv * (1.0 + k * 0.35 * r2);
        }

        half4 main(float2 fragCoord) {
            float2 res = max(iResolution, float2(1.0, 1.0));
            float minDim = min(res.x, res.y);
            float2 center = 0.5 * res + float2(iShakeX, iShakeY);
            float2 uv = (fragCoord - center) / minDim;

            if (iCrtBarrel > 0.001) {
                uv = applyBarrel(uv, iCrtBarrel);
            }

            // MilkDrop 4-way Quad Mirror
            if (iQuadMirrorBlend > 0.005) {
                float2 qUv = abs(uv - float2(iQuadOffset)) - float2(0.0);
                uv = mix(uv, qUv, iQuadMirrorBlend);
            }

            // MilkDrop Polar Unwrap projection
            if (iPolarBlend > 0.005) {
                float r = length(uv);
                float a = atan(uv.y, uv.x) / 6.283185 + 0.5;
                float2 polarUv = float2(
                    fract(a * floor(iPolarArms + 0.5) + iTime * iPolarSpin * 0.15) - 0.5,
                    clamp(r * 1.15 - 0.5, -0.5, 0.5)
                );
                uv = mix(uv, polarUv, iPolarBlend);
            }

            // MilkDrop Anamorphic Stretch + Tunnel Pull
            if (abs(iStretchX - 1.0) > 0.005 || abs(iStretchY - 1.0) > 0.005) {
                uv = float2(uv.x / max(iStretchX, 0.35), uv.y / max(iStretchY, 0.35));
            }
            if (abs(iTunnelZoom - 1.0) > 0.005 || abs(iTunnelPulse) > 0.005) {
                float rad = length(uv);
                float zf = 1.0 + (iTunnelZoom - 1.0) * pow(max(rad, 0.001), iTunnelZoomExp) + iTunnelPulse * (1.0 - rad);
                uv /= max(zf, 0.4);
            }

            // MilkDrop Angular Pinwheel Twist
            if (abs(iPinwheelRot) > 0.005) {
                float rad = length(uv);
                float ang = atan(uv.y, uv.x);
                float rotAng = iPinwheelRot * 0.45 * sin(rad * iPinwheelRadFreq - iTime * 1.6)
                             + iPinwheelRot * 0.18 * cos(ang * floor(iPinwheelPetals + 0.5));
                float cs = cos(rotAng);
                float sn = sin(rotAng);
                uv = float2(uv.x * cs - uv.y * sn, uv.x * sn + uv.y * cs);
            }

            // Vortex Swirl
            if (abs(iVortexTwist) > 0.001) {
                float2 orbitCenter = float2(cos(iTime * 0.9), sin(iTime * 0.7)) * iVortexOrbit;
                float2 d = uv - orbitCenter;
                float dist = length(d);
                float falloff = smoothstep(iVortexRadius, 0.0, dist);
                float ang = iVortexTwist * falloff;
                float cs = cos(ang);
                float sn = sin(ang);
                d = float2(d.x * cs - d.y * sn, d.x * sn + d.y * cs);
                uv = d + orbitCenter;
            }

            // Ripple Interference
            if (iRippleAmp > 0.001) {
                float dist = length(uv);
                float wave = sin(dist * iRippleFreq * 3.14159 - iTime * iRippleSpeed * 6.0);
                float atten = exp(-dist * 1.4);
                uv += normalize(uv + float2(0.0001, 0.0001)) * (wave * iRippleAmp * 0.045 * atten);
            }

            // Wave Fold & Phase Warp + MilkDrop Liquid Warp
            if (iWaveFoldAmp > 0.001) {
                float phase = iTime * iWaveFoldSpeed * 3.0;
                float dx = sin(uv.y * iWaveFoldFreq + phase) * iWaveFoldAmp * 0.05;
                float dy = cos(uv.x * iWaveFoldFreq - phase * 0.8) * iWaveFoldAmp * 0.05;
                uv += float2(dx, dy);
            }
            if (iMilkWarpAmt > 0.005) {
                float wt = iTime * iMilkWarpSpeed * 1.6;
                float wx = sin(uv.y * iMilkWarpScale * 2.5 + wt) * cos(uv.x * iMilkWarpScale * 1.7 - wt * 0.6);
                float wy = cos(uv.x * iMilkWarpScale * 2.5 - wt * 0.8) * sin(uv.y * iMilkWarpScale * 1.7 + wt * 0.5);
                uv += float2(wx, wy) * (iMilkWarpAmt * 0.045);
            }

            float2 sampleCoord = uv * minDim + 0.5 * res;
            sampleCoord = clamp(sampleCoord, float2(0.0, 0.0), res - float2(1.0, 1.0));

            half4 color;
            if (iChromaticPx > 0.25) {
                float2 dir = float2(cos(iChromaticAngleRad), sin(iChromaticAngleRad)) * iChromaticPx;
                float2 rCoord = clamp(sampleCoord + dir * iChromaticSpread, float2(0.0), res - float2(1.0));
                float2 bCoord = clamp(sampleCoord - dir * iChromaticSpread, float2(0.0), res - float2(1.0));
                half4 cR = background.eval(rCoord);
                half4 cG = background.eval(sampleCoord);
                half4 cB = background.eval(bCoord);
                color = half4(cR.r, cG.g, cB.b, max(cG.a, max(cR.a, cB.a)));
            } else {
                color = background.eval(sampleCoord);
            }

            // MilkDrop Solarize Color Fold
            if (iSolarizeMix > 0.005) {
                float3 folded = abs(sin(color.rgb * (iSolarizeFreq * 3.14159) + iSolarizePhase));
                color.rgb = mix(color.rgb, folded, iSolarizeMix);
            }

            // MilkDrop Posterize Topographic Contour Bands
            if (iPosterizeMix > 0.005) {
                float lvls = max(2.0, floor(iPosterizeLevels + 0.5));
                float3 qCol = floor(color.rgb * lvls + 0.5) / lvls;
                float edge = length(color.rgb - qCol) * lvls * iPosterizeEdge;
                color.rgb = mix(color.rgb, clamp(qCol + edge * 0.35, 0.0, 1.0), iPosterizeMix);
            }

            if (iQuadMirrorBlend > 0.005 && iQuadInvert > 0.005) {
                float lum = dot(color.rgb, float3(0.299, 0.587, 0.114));
                if (fragCoord.x < center.x && fragCoord.y < center.y || fragCoord.x > center.x && fragCoord.y > center.y) {
                    color.rgb = mix(color.rgb, float3(1.0) - color.rgb * (0.5 + lum * 0.5), iQuadInvert * 0.65);
                }
            }

            if (iCrtDepth > 0.005) {
                float scan = 0.5 + 0.5 * sin(fragCoord.y * (6.28318 / max(2.0, iCrtSpacing)));
                float mask = mix(1.0, 0.45 + 0.55 * scan, iCrtDepth);
                color.rgb *= mask;
            }

            return color;
        }
    """

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    class Api33ShaderHolder {
        val nebulaShader = RuntimeShader(NEBULA_PLASMA_SRC)
        val nebulaBrush = ShaderBrush(nebulaShader)
        val feedbackWarpShader = RuntimeShader(FEEDBACK_WARP_SRC)
        val feedbackWarpBrush = ShaderBrush(feedbackWarpShader)
        val compositeShader = RuntimeShader(COMPOSITE_FX_SRC)

        fun updateNebulaUniforms(
            width: Float,
            height: Float,
            timeSec: Float,
            turbulence: Float,
            warpFlow: Float,
            glowDensity: Float,
            bass: Float,
            treble: Float,
            colorA: Color,
            colorB: Color,
            colorC: Color,
            opacity: Float,
        ) {
            nebulaShader.setFloatUniform("iResolution", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
            nebulaShader.setFloatUniform("iTime", timeSec)
            nebulaShader.setFloatUniform("iTurbulence", turbulence)
            nebulaShader.setFloatUniform("iWarpFlow", warpFlow)
            nebulaShader.setFloatUniform("iGlowDensity", glowDensity)
            nebulaShader.setFloatUniform("iBass", bass)
            nebulaShader.setFloatUniform("iTreble", treble)
            nebulaShader.setFloatUniform("iColorA", colorA.red, colorA.green, colorA.blue, 1f)
            nebulaShader.setFloatUniform("iColorB", colorB.red, colorB.green, colorB.blue, 1f)
            nebulaShader.setFloatUniform("iColorC", colorC.red, colorC.green, colorC.blue, 1f)
            nebulaShader.setFloatUniform("iOpacity", opacity.coerceIn(0f, 1f))
        }

        fun updateFeedbackWarpBrush(
            prevBitmap: Bitmap,
            width: Float,
            height: Float,
            timeSec: Float,
            decay: Float,
            echoZoom: Float,
            echoRotRad: Float,
            milkWarpAmt: Float,
            milkWarpScale: Float,
            milkWarpSpeed: Float,
            tunnelZoom: Float,
            tunnelZoomExp: Float,
            tunnelPulse: Float,
            stretchX: Float,
            stretchY: Float,
            panDx: Float,
            panDy: Float,
            pinwheelRot: Float,
            pinwheelRadFreq: Float,
            pinwheelPetals: Float,
        ): ShaderBrush {
            val bmpShader = BitmapShader(prevBitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            feedbackWarpShader.setInputShader("prevFrame", bmpShader)
            feedbackWarpShader.setFloatUniform("iResolution", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
            feedbackWarpShader.setFloatUniform("iTime", timeSec)
            feedbackWarpShader.setFloatUniform("iDecay", decay)
            feedbackWarpShader.setFloatUniform("iEchoZoom", echoZoom)
            feedbackWarpShader.setFloatUniform("iEchoRotRad", echoRotRad)
            feedbackWarpShader.setFloatUniform("iMilkWarpAmt", milkWarpAmt)
            feedbackWarpShader.setFloatUniform("iMilkWarpScale", milkWarpScale)
            feedbackWarpShader.setFloatUniform("iMilkWarpSpeed", milkWarpSpeed)
            feedbackWarpShader.setFloatUniform("iTunnelZoom", tunnelZoom)
            feedbackWarpShader.setFloatUniform("iTunnelZoomExp", tunnelZoomExp)
            feedbackWarpShader.setFloatUniform("iTunnelPulse", tunnelPulse)
            feedbackWarpShader.setFloatUniform("iStretchX", stretchX)
            feedbackWarpShader.setFloatUniform("iStretchY", stretchY)
            feedbackWarpShader.setFloatUniform("iPanDx", panDx)
            feedbackWarpShader.setFloatUniform("iPanDy", panDy)
            feedbackWarpShader.setFloatUniform("iPinwheelRot", pinwheelRot)
            feedbackWarpShader.setFloatUniform("iPinwheelRadFreq", pinwheelRadFreq)
            feedbackWarpShader.setFloatUniform("iPinwheelPetals", pinwheelPetals)
            return feedbackWarpBrush
        }

        fun buildCompositeRenderEffect(
            width: Float,
            height: Float,
            timeSec: Float,
            waveFoldAmp: Float,
            waveFoldFreq: Float,
            waveFoldSpeed: Float,
            vortexTwist: Float,
            vortexRadius: Float,
            vortexOrbit: Float,
            rippleAmp: Float,
            rippleFreq: Float,
            rippleSpeed: Float,
            chromaticPx: Float,
            chromaticAngleRad: Float,
            chromaticSpread: Float,
            crtDepth: Float,
            crtSpacing: Float,
            crtBarrel: Float,
            shakeX: Float,
            shakeY: Float,
            milkWarpAmt: Float,
            milkWarpScale: Float,
            milkWarpSpeed: Float,
            tunnelZoom: Float,
            tunnelZoomExp: Float,
            tunnelPulse: Float,
            stretchX: Float,
            stretchY: Float,
            pinwheelRot: Float,
            pinwheelRadFreq: Float,
            pinwheelPetals: Float,
            quadMirrorBlend: Float,
            quadOffset: Float,
            quadInvert: Float,
            polarBlend: Float,
            polarArms: Float,
            polarSpin: Float,
            solarizeMix: Float,
            solarizeFreq: Float,
            solarizePhase: Float,
            posterizeMix: Float,
            posterizeLevels: Float,
            posterizeEdge: Float,
        ): RenderEffect? {
            val anyActive = waveFoldAmp > 0.005f ||
                kotlin.math.abs(vortexTwist) > 0.005f ||
                rippleAmp > 0.005f ||
                chromaticPx > 0.25f ||
                crtDepth > 0.005f ||
                crtBarrel > 0.005f ||
                kotlin.math.abs(shakeX) > 0.2f ||
                kotlin.math.abs(shakeY) > 0.2f ||
                milkWarpAmt > 0.005f ||
                kotlin.math.abs(tunnelZoom - 1f) > 0.005f ||
                kotlin.math.abs(tunnelPulse) > 0.005f ||
                kotlin.math.abs(stretchX - 1f) > 0.005f ||
                kotlin.math.abs(stretchY - 1f) > 0.005f ||
                kotlin.math.abs(pinwheelRot) > 0.005f ||
                quadMirrorBlend > 0.005f ||
                polarBlend > 0.005f ||
                solarizeMix > 0.005f ||
                posterizeMix > 0.005f
            if (!anyActive) return null

            compositeShader.setFloatUniform("iResolution", width.coerceAtLeast(1f), height.coerceAtLeast(1f))
            compositeShader.setFloatUniform("iTime", timeSec)
            compositeShader.setFloatUniform("iWaveFoldAmp", waveFoldAmp)
            compositeShader.setFloatUniform("iWaveFoldFreq", waveFoldFreq)
            compositeShader.setFloatUniform("iWaveFoldSpeed", waveFoldSpeed)
            compositeShader.setFloatUniform("iVortexTwist", vortexTwist)
            compositeShader.setFloatUniform("iVortexRadius", vortexRadius)
            compositeShader.setFloatUniform("iVortexOrbit", vortexOrbit)
            compositeShader.setFloatUniform("iRippleAmp", rippleAmp)
            compositeShader.setFloatUniform("iRippleFreq", rippleFreq)
            compositeShader.setFloatUniform("iRippleSpeed", rippleSpeed)
            compositeShader.setFloatUniform("iChromaticPx", chromaticPx)
            compositeShader.setFloatUniform("iChromaticAngleRad", chromaticAngleRad)
            compositeShader.setFloatUniform("iChromaticSpread", chromaticSpread)
            compositeShader.setFloatUniform("iCrtDepth", crtDepth)
            compositeShader.setFloatUniform("iCrtSpacing", crtSpacing)
            compositeShader.setFloatUniform("iCrtBarrel", crtBarrel)
            compositeShader.setFloatUniform("iShakeX", shakeX)
            compositeShader.setFloatUniform("iShakeY", shakeY)
            compositeShader.setFloatUniform("iMilkWarpAmt", milkWarpAmt)
            compositeShader.setFloatUniform("iMilkWarpScale", milkWarpScale)
            compositeShader.setFloatUniform("iMilkWarpSpeed", milkWarpSpeed)
            compositeShader.setFloatUniform("iTunnelZoom", tunnelZoom)
            compositeShader.setFloatUniform("iTunnelZoomExp", tunnelZoomExp)
            compositeShader.setFloatUniform("iTunnelPulse", tunnelPulse)
            compositeShader.setFloatUniform("iStretchX", stretchX)
            compositeShader.setFloatUniform("iStretchY", stretchY)
            compositeShader.setFloatUniform("iPinwheelRot", pinwheelRot)
            compositeShader.setFloatUniform("iPinwheelRadFreq", pinwheelRadFreq)
            compositeShader.setFloatUniform("iPinwheelPetals", pinwheelPetals)
            compositeShader.setFloatUniform("iQuadMirrorBlend", quadMirrorBlend)
            compositeShader.setFloatUniform("iQuadOffset", quadOffset)
            compositeShader.setFloatUniform("iQuadInvert", quadInvert)
            compositeShader.setFloatUniform("iPolarBlend", polarBlend)
            compositeShader.setFloatUniform("iPolarArms", polarArms)
            compositeShader.setFloatUniform("iPolarSpin", polarSpin)
            compositeShader.setFloatUniform("iSolarizeMix", solarizeMix)
            compositeShader.setFloatUniform("iSolarizeFreq", solarizeFreq)
            compositeShader.setFloatUniform("iSolarizePhase", solarizePhase)
            compositeShader.setFloatUniform("iPosterizeMix", posterizeMix)
            compositeShader.setFloatUniform("iPosterizeLevels", posterizeLevels)
            compositeShader.setFloatUniform("iPosterizeEdge", posterizeEdge)

            return RenderEffect.createRuntimeShaderEffect(compositeShader, "background")
        }
    }
}
