<template>
  <div class="page">
    <div class="topbar">
      <h1>AI植物病虫害智能监测识别系统</h1>
    </div>

    <div class="content">
      <section class="panel">
        <div class="panel-header">
          <div class="title">摄像头监测</div>
          <div class="badge" :class="cameraStateBadgeClass">
            <span class="badge-dot" />
            <span>{{ cameraStatusText }}</span>
          </div>
        </div>
        <div class="panel-body">
          <div class="camera-wrap">
            <video ref="videoRef" autoplay playsinline muted></video>
            <canvas ref="canvasRef" style="display: none"></canvas>
            <div class="camera-overlay">
              <div>Status: {{ localPreprocessStatus }}</div>
              <div v-if="cfg.debugMode === 1" style="margin-top: 8px; line-height: 1.4">
                <div>greenRatio: {{ Number(debugInfo.greenRatio || 0).toFixed(4) }}</div>
                <div>leafDetected: {{ debugInfo.leafDetected ? '是' : '否' }}</div>
                <div>abnormalRatio: {{ Number(debugInfo.abnormalRatio || 0).toFixed(4) }}</div>
                <div>
                  yellowRatio: {{ Number(debugInfo.yellowRatio || 0).toFixed(4) }} / brownRatio:
                  {{ Number(debugInfo.brownRatio || 0).toFixed(4) }}
                </div>
                <div v-if="debugInfo.reason" style="color: #ffd666; margin-top: 6px">{{ debugInfo.reason }}</div>
              </div>
            </div>
          </div>

          <div style="margin-top: 12px" class="row">
            <div class="field">
              <label>检测阈值(叶片置信度)</label>
              <input type="number" step="0.05" v-model.number="cfg.leafScoreThreshold" />
            </div>
            <div class="field">
              <label>颜色异常像素阈值</label>
              <input type="number" step="0.01" v-model.number="cfg.abnormalRatioThreshold" />
            </div>
            <div class="field">
              <label>连续异常帧数(防抖)</label>
              <input type="number" step="1" v-model.number="cfg.debounceFrames" />
            </div>
            <div class="field">
              <label>抓拍锁定秒(10秒内不重复)</label>
              <input type="number" step="1" v-model.number="cfg.lockoutSeconds" />
            </div>
            <div class="field" style="min-width: 210px">
              <label>调试模式(显示关键数值)</label>
              <select v-model.number="cfg.debugMode">
                <option :value="0">关闭</option>
                <option :value="1">开启</option>
              </select>
            </div>
          </div>

          <div style="margin-top: 12px" class="row">
            <button class="btn primary" @click="toggleCamera">{{ cameraButtonText }}</button>
            <button class="btn" @click="refreshHistory">刷新历史</button>
            <button class="btn" @click="exportExcel">导出Excel</button>
          </div>

          <div style="margin-top: 10px" class="status">
            检测逻辑严格执行：先检测叶片→再做叶片颜色异常分析；只有“两者同时满足”才会抓拍上传，且抓拍后会有锁定防重复上传。
          </div>
        </div>
      </section>

      <aside class="panel">
        <div class="panel-header">
          <div class="title">识别结果</div>
          <button class="btn" @click="clearResult">清空</button>
        </div>
        <div class="panel-body">
          <div v-if="currentResult" class="alarm" :style="currentResult.isAlert ? '' : 'display:none'">
            <h2>异常告警</h2>
            <p>{{ currentResult.alertText }}</p>
          </div>

          <div v-if="currentResult && !currentResult.isAlert" class="status">
            当前未检测到告警性病虫害异常（或仍在模型/上传处理中）。
          </div>

          <div v-if="currentResult" style="margin-top: 10px">
            <div class="badge ok" v-if="currentResult.isAlert" style="margin-bottom: 10px">
              <span class="badge-dot" />
              <span>识别完成</span>
            </div>
            <div class="status">
              <div>作物类型：{{ currentResult.cropType }}</div>
              <div>病虫害名称：{{ currentResult.diseaseName }}</div>
              <div>危害等级：{{ currentResult.hazardLevel }}</div>
              <div style="margin-top: 8px; white-space: pre-wrap">{{ currentResult.preventionAdvice }}</div>
            </div>
            <div v-if="currentResult.imageUrl" style="margin-top: 10px">
              <div class="status" style="margin-bottom: 8px">图片预览：</div>
              <img :src="currentResult.imageUrl" alt="recognition" style="width: 100%; border-radius: 12px" />
            </div>
          </div>

          <div style="margin-top: 16px">
            <div class="panel-header" style="border: 0; padding: 0; background: transparent">
              <div class="title">历史记录</div>
            </div>

            <div class="list" style="margin-top: 10px">
              <div v-for="item in records" :key="item.id" class="item" @click="selectRecord(item)">
                <div class="meta">
                  <div class="name">{{ item.diseaseName || '—' }}</div>
                  <div class="time">{{ formatTime(item.createTime) }}</div>
                </div>
                <div class="sub">
                  作物：{{ item.cropType }}；危害等级：{{ item.hazardLevel }}；告警：{{ item.isAlert ? '是' : '否' }}
                </div>
              </div>
            </div>

            <div v-if="records.length === 0" class="status" style="margin-top: 10px">
              暂无历史记录。触发异常后会自动上传并生成记录。
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import * as XLSX from 'xlsx'
import { fetchConfig, fetchRecords, recognizeByImageBase64 } from './api'
import { runLeafDetectionLite, analyzeLeafAbnormalByColor, captureJpegBase64 } from './detection'

const videoRef = ref(null)
const canvasRef = ref(null)

const cameraOn = ref(false)
const cameraStatusText = ref('未启动')
const localPreprocessStatus = ref('等待摄像头')
const cfg = ref({
  leafScoreThreshold: 0.4,
  abnormalRatioThreshold: 0.23,
  debounceFrames: 3,
  lockoutSeconds: 10,
  // 是否启用声音告警（可关闭）
  alarmSoundEnabled: true,
  // 0/1：前端调试开关。开启后显示 greenRatio/异常比例等。
  debugMode: 0,
})

const currentResult = ref(null)
const records = ref([])
let selectedRecord = null

const debugInfo = ref({
  greenRatio: 0,
  leafDetected: false,
  abnormalRatio: 0,
  yellowRatio: 0,
  brownRatio: 0,
  reason: '',
})

const cameraButtonText = computed(() => (cameraOn.value ? '停止监测' : '开始监测'))
const cameraStateBadgeClass = computed(() => (cameraOn.value ? 'ok' : 'danger'))

function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString()
}

async function startCamera(facingMode = 'environment') {
  const video = videoRef.value
  if (!video) return
  const canvas = canvasRef.value
  const ctx = canvas.getContext('2d', { willReadFrequently: true })

  // 防止重复启动
  if (cameraOn.value) return

  const stream = await navigator.mediaDevices.getUserMedia({
    video: {
      facingMode,
      width: { ideal: 1280 },
      height: { ideal: 720 },
    },
    audio: false,
  })

  video.srcObject = stream

  await new Promise((resolve) => {
    video.onloadedmetadata = () => resolve()
  })

  // 统一画布分辨率（用于检测与抓拍）
  const vw = video.videoWidth || 1280
  const vh = video.videoHeight || 720
  canvas.width = vw
  canvas.height = vh

  cameraOn.value = true
  cameraStatusText.value = '运行中'
  localPreprocessStatus.value = '检测中...'

  return { stream, ctx }
}

function stopCamera() {
  const video = videoRef.value
  const stream = video?.srcObject
  if (stream && typeof stream.getTracks === 'function') {
    for (const t of stream.getTracks()) t.stop()
  }
  if (video) video.srcObject = null
  cameraOn.value = false
  cameraStatusText.value = '未启动'
}

let timerId = null
let abnormalCount = 0
let lastCaptureAt = 0

async function runLoop() {
  const video = videoRef.value
  const canvas = canvasRef.value
  if (!video || !canvas) return
  const ctx = canvas.getContext('2d', { willReadFrequently: true })

  const tickMs = 500

  async function tick() {
    if (!cameraOn.value) return
    if (document.hidden) return

    // 1) 抓当前帧到 canvas
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height)

    // 2) 第一步：轻量模型检测叶片是否存在（绿色像素比例）
    localPreprocessStatus.value = '检测叶片(绿色像素)...'
    const leafDetection = await runLeafDetectionLite(canvas, {
      greenRatioThreshold: cfg.value.leafScoreThreshold,
    })

    if (cfg.value.debugMode === 1) {
      debugInfo.value.greenRatio = Number(leafDetection.greenRatio || 0)
      debugInfo.value.leafDetected = Boolean(leafDetection.hasLeaf)
      debugInfo.value.reason = ''
      console.log('[leaf-detect]', {
        greenRatio: debugInfo.value.greenRatio,
        threshold: cfg.value.leafScoreThreshold,
        hasLeaf: debugInfo.value.leafDetected,
      })
    }

    if (!leafDetection.hasLeaf) {
      abnormalCount = 0
      localPreprocessStatus.value = '无叶片，跳过'
      if (cfg.value.debugMode === 1) debugInfo.value.reason = 'greenRatio未达到阈值'
      return
    }

    // 3) 第二步：对叶片区域像素颜色分析
    localPreprocessStatus.value = '分析叶片颜色异常...'
    const color = analyzeLeafAbnormalByColor(ctx, leafDetection.leafRois, {
      yellowRatioThreshold: cfg.value.abnormalRatioThreshold * 0.6,
      brownRatioThreshold: cfg.value.abnormalRatioThreshold * 0.6,
    })

    if (cfg.value.debugMode === 1) {
      debugInfo.value.abnormalRatio = Number(color.abnormalRatio || 0)
      debugInfo.value.yellowRatio = Number(color.yellowRatio || 0)
      debugInfo.value.brownRatio = Number(color.brownRatio || 0)
      console.log('[color-analyze]', {
        abnormalRatio: debugInfo.value.abnormalRatio,
        yellowRatio: debugInfo.value.yellowRatio,
        brownRatio: debugInfo.value.brownRatio,
        abnormal: Boolean(color.abnormal),
      })
    }

    if (!color.abnormal) {
      abnormalCount = 0
      localPreprocessStatus.value = '颜色无异常，跳过'
      if (cfg.value.debugMode === 1) debugInfo.value.reason = '颜色异常像素占比未达到阈值'
      return
    }

    abnormalCount++
    if (abnormalCount < cfg.value.debounceFrames) {
      localPreprocessStatus.value = `疑似异常：防抖中 ${abnormalCount}/${cfg.value.debounceFrames}`
      if (cfg.value.debugMode === 1) debugInfo.value.reason = '防抖未达连续帧要求'
      return
    }

    // 4) 防重复：10秒内不再重复触发
    const now = Date.now()
    if (now - lastCaptureAt < cfg.value.lockoutSeconds * 1000) {
      localPreprocessStatus.value = '已触发过抓拍：锁定期内跳过'
      abnormalCount = 0
      if (cfg.value.debugMode === 1) debugInfo.value.reason = '锁定期内跳过（避免重复上传）'
      return
    }

    lastCaptureAt = now
    abnormalCount = 0
    localPreprocessStatus.value = '抓拍并上传识别中...'

    // 5) 抓拍高清画面：base64（前端本地压缩降低体积）
    const imageBase64 = captureJpegBase64(canvas, { scaleWidth: 1280, jpegQuality: 0.86 })
    const loadingStartedAt = Date.now()

    try {
      const resp = await recognizeByImageBase64({ imageBase64 })
      currentResult.value = resp?.data || null
      if (cfg.value.debugMode === 1) debugInfo.value.reason = '已抓拍并触发识别'
      if (currentResult.value?.isAlert && cfg.value.alarmSoundEnabled) {
        // 简单蜂鸣：可在浏览器权限/策略下工作
        try {
          const audioCtx = new (window.AudioContext || window.webkitAudioContext)()
          const oscillator = audioCtx.createOscillator()
          const gain = audioCtx.createGain()
          oscillator.type = 'sine'
          oscillator.frequency.value = 880
          gain.gain.value = 0.03
          oscillator.connect(gain)
          gain.connect(audioCtx.destination)
          oscillator.start()
          setTimeout(() => {
            oscillator.stop()
            audioCtx.close()
          }, 180)
        } catch {
          // ignore
        }
      }
      await refreshHistory()
      const elapsed = Date.now() - loadingStartedAt
      localPreprocessStatus.value = `识别完成（${elapsed}ms）`
    } catch (e) {
      localPreprocessStatus.value = `上传/识别失败：${e?.message || e}`
      if (cfg.value.debugMode === 1) debugInfo.value.reason = `上传/识别失败：${e?.message || e}`
    }
  }

  timerId = setInterval(() => {
    // 不要堆积并发：上一帧没结束就跳过
    if (runLoop._inFlight) return
    runLoop._inFlight = true
    tick()
      .catch(() => {})
      .finally(() => {
        runLoop._inFlight = false
      })
  }, tickMs)
}

async function toggleCamera() {
  if (cameraOn.value) {
    if (timerId) clearInterval(timerId)
    timerId = null
    stopCamera()
    localPreprocessStatus.value = '已停止监测'
    return
  }

  // 摄像头
  try {
    await startCamera('environment')
  } catch (e) {
    cameraStatusText.value = '摄像头授权失败或设备不可用'
    localPreprocessStatus.value = `错误：${e?.message || e}`
    return
  }

  await runLoop()
}

function clearResult() {
  currentResult.value = null
}

async function refreshHistory() {
  try {
    const resp = await fetchRecords({ limit: 20 })
    records.value = resp?.data?.items || []
  } catch {
    // ignore
  }
}

function selectRecord(item) {
  selectedRecord = item
  currentResult.value = {
    ...item,
    preventionAdvice: item.preventionAdvice || item.advice || '',
    diseaseName: item.diseaseName || '',
    cropType: item.cropType || '',
    hazardLevel: item.hazardLevel || '',
    alertText: item.isAlert ? '检测到疑似病虫害异常，请尽快进行田间核查。' : '',
    isAlert: Boolean(item.isAlert),
    imageUrl: item.imageUrl || item.imagePresignedUrl || null,
  }
}

function exportExcel() {
  // 仅导出当前加载的历史记录
  try {
    const data = records.value.map((r) => ({
      作物类型: r.cropType,
      病虫害名称: r.diseaseName,
      危害等级: r.hazardLevel,
      是否告警: r.isAlert ? '是' : '否',
      创建时间: formatTime(r.createTime),
    }))

    const ws = XLSX.utils.json_to_sheet(data)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, 'history')
    XLSX.writeFile(wb, `plant_disease_history_${Date.now()}.xlsx`)
  } catch (e) {
    localPreprocessStatus.value = `导出Excel失败：${e?.message || e}`
  }
}

onMounted(async () => {
  // 拉取后端配置（作业参数可配置）
  try {
    const resp = await fetchConfig()
    if (resp?.data) cfg.value = { ...cfg.value, ...resp.data }
  } catch {
    // ignore: 使用前端默认值
  }

  // 初始化画布以备检测
  const canvas = canvasRef.value
  if (canvas) {
    canvas.width = 1280
    canvas.height = 720
  }

  await refreshHistory()

  // 页面隐藏自动暂停检测
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) localPreprocessStatus.value = '页面已隐藏：暂停检测'
    else localPreprocessStatus.value = cameraOn.value ? '检测中...' : '等待摄像头'
  })
})

onBeforeUnmount(() => {
  if (timerId) clearInterval(timerId)
  timerId = null
  stopCamera()
})
</script>

