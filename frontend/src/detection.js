import * as tf from '@tensorflow/tfjs'

// 轻量叶片检测（更灵活）：
// 1) 不是死盯“纯绿色”，而是用 ExG 植被指数（2G - R - B），对偏黄叶片更友好
// 2) 比例不再除以“全帧”，而是只在“彩色像素集合”（非白/非灰）里统计，从而抗背景
export async function runLeafDetectionLite(
  canvas,
  {
    greenRatioThreshold = 0.12,
    // ExG 在 [~ -1, 2]；越大表示越像植被
    exgThreshold = 0.02,
    // 亮度与饱和度阈值，用于过滤掉白/灰背景
    brightnessThreshold = 0.16, // 对应 (r+g+b)/3 >= 0.16
    satThreshold = 0.06, // max-min >= 0.06
    minCandidateRatio = 0.02, // candidate 太少时认为无叶片
  } = {}
) {
  const w = canvas.width
  const h = canvas.height
  if (!w || !h) {
    return { hasLeaf: false, leafRois: [], topScore: 0 }
  }

  const result = tf.tidy(() => {
    const img = tf.browser.fromPixels(canvas).toFloat()
    const small = tf.image.resizeBilinear(img, [160, 90], true)
    const rgb = small.div(255)
    const [r, g, b] = tf.split(rgb, 3, 2) // each: [H,W,1]

    const brightness = r.add(g).add(b).div(3) // [H,W,1]
    const maxRGB = tf.maximum(tf.maximum(r, g), b)
    const minRGB = tf.minimum(tf.minimum(r, g), b)
    const sat = maxRGB.sub(minRGB) // [H,W,1]

    // ExG: 2G - R - B
    const exg = g.mul(2).sub(r).sub(b) // [H,W,1]

    const candidate = brightness.greaterEqual(brightnessThreshold).logicalAnd(sat.greaterEqual(satThreshold)).squeeze() // [H,W]
    const vegetation = candidate.logicalAnd(exg.squeeze().greaterEqual(exgThreshold)) // [H,W]

    const candidateCount = tf.sum(candidate)
    const vegetationCount = tf.sum(vegetation)

    const totalPixels = tf.scalar(candidate.shape[0] * candidate.shape[1])
    const candidateRatio = candidateCount.div(totalPixels)

    // 避免候选像素太少时出现偶然比例偏高
    const safeVegetationRatio = tf.where(
      candidateRatio.greaterEqual(minCandidateRatio),
      vegetationCount.div(candidateCount.add(1e-6)),
      tf.scalar(0)
    )

    return { ratio: safeVegetationRatio }
  })

  const greenRatio = (await result.ratio.data())[0]
  result.ratio.dispose?.()

  const hasLeaf = greenRatio >= greenRatioThreshold
  const leafRois = hasLeaf ? [{ x1: 0, y1: 0, x2: w - 1, y2: h - 1 }] : []

  return {
    hasLeaf,
    leafRois,
    greenRatio,
    topScore: hasLeaf ? greenRatio : 0,
  }
}

export function analyzeLeafAbnormalByColor(ctx, leafRois, { yellowRatioThreshold = 0.12, brownRatioThreshold = 0.1 }) {
  let total = 0
  let yellow = 0
  let brown = 0

  for (const roi of leafRois) {
    const w = roi.x2 - roi.x1
    const h = roi.y2 - roi.y1
    if (w <= 0 || h <= 0) continue

    const imageData = ctx.getImageData(roi.x1, roi.y1, w, h)
    const data = imageData.data
    const pixels = data.length / 4
    for (let p = 0; p < data.length; p += 4) {
      const r = data[p]
      const g = data[p + 1]
      const b = data[p + 2]

      const isYellow = r > 120 && g > 105 && b < 110 && Math.abs(r - g) < 70
      const isBrown = r > 85 && g > 55 && b < 90 && r - g > 10

      if (isYellow) yellow++
      if (isBrown) brown++
    }
    total += pixels
  }

  if (total <= 0) return { abnormal: false, abnormalRatio: 0, yellowRatio: 0, brownRatio: 0 }

  const yellowRatio = yellow / total
  const brownRatio = brown / total
  const abnormalRatio = yellowRatio + brownRatio
  const abnormal =
    abnormalRatio >= Math.max(yellowRatioThreshold + brownRatioThreshold, yellowRatioThreshold) &&
    (yellowRatio >= yellowRatioThreshold || brownRatio >= brownRatioThreshold)

  return { abnormal: Boolean(abnormal), abnormalRatio, yellowRatio, brownRatio }
}

export function captureJpegBase64(canvas, { scaleWidth = 1280, jpegQuality = 0.86 } = {}) {
  const srcW = canvas.width || scaleWidth
  const srcH = canvas.height || Math.round((scaleWidth * 9) / 16)
  const targetW = Math.min(scaleWidth, srcW)
  const targetH = Math.round((srcH * targetW) / srcW)

  const tmp = document.createElement('canvas')
  tmp.width = targetW
  tmp.height = targetH
  const tctx = tmp.getContext('2d')
  tctx.drawImage(canvas, 0, 0, targetW, targetH)

  return tmp.toDataURL('image/jpeg', jpegQuality)
}

