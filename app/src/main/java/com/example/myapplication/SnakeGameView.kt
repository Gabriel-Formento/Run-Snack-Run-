package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class BloodParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var maxLife: Float,
    val color: Int,
    var radius: Float
)

class Footprint(
    var x: Float,
    var y: Float,
    var angle: Float,
    var spawnTime: Long,
    var type: Int
)

class SnakeGameView(context: Context) : View(context) {

    private var snake = mutableListOf(Point(10, 10))
    private var food = Point(5, 5)

    // VARIÁVEIS DO MODO SURVIVAL E DELAYS
    var isSurvivalMode = false
    private var hamsterPos = Point(10, 15)
    private var cherryPos = Point(5, 5)
    private var hamsterDirection = Direction.UP

    // ACUMULADORES DE VELOCIDADE (Exatamente 35% mais lentos)
    private var survivalSnakeMoveAccumulator = 0f
    private var hamsterMoveAccumulator = 0f

    private var previousFood = Point(5, 5)
    private var lastFoodMoveTime = 0L
    private var hamsterAngle = 0f

    private var foodRespawnTimer = 0L

    private val bloodParticles = mutableListOf<BloodParticle>()
    var isBloodEnabled = false

    private val footprints = mutableListOf<Footprint>()
    var isFootprintsEnabled = true

    var isDiagonalEnabled = false
    var isInfiniteMode = false

    private val internalWalls = mutableListOf<Point>()
    private var infiniteWallStages = 0

    private var bigFood: Point? = null
    private var bigFoodTimeLeft = 0L
    private val maxBigFoodTime = 10000L // 10 Segundos

    private var hamstersEatenThisGame = 0
    private var totalScore = 0
    private var startTimeMs = 0L

    private var eatTimestamps = mutableListOf<Long>()
    var achievedFastEat = false

    private val gridSize = 20

    private val gridH: Int
        get() {
            val b = if (width > 0) width / gridSize else 0
            return if (b > 0 && height > 0) height / b else 20
        }

    private var direction = Direction.RIGHT
    var isGameOver = false
    var isPaused = false
    var isAutoPlay = false
    var useTouch = true
    var useInfiniteWalls = true
    var isHighQuality = false
    var isAttractMode = false
    var onExitAttractModeCallback: (() -> Unit)? = null

    private var currentSnakeSkin = 0
    private var currentFieldSkin = 0
    private var currentFoodSkinId = 0
    private var currentBonusFoodClass = 0
    private var currentBigFoodSkinId = 0

    private var hqHeadBitmap: Bitmap? = null
    private var hqBodyBitmap: Bitmap? = null
    private var hqTailBitmap: Bitmap? = null
    private var hqCurveBitmap: Bitmap? = null

    private var hqFieldBitmap: Bitmap? = null
    private var hqFootprintBitmap: Bitmap? = null
    private var hqFootprint2Bitmap: Bitmap? = null
    private var hqCherryBitmap: Bitmap? = null

    private val hqHamNormal1 = arrayOfNulls<Bitmap>(11)
    private val hqHamNormal2 = arrayOfNulls<Bitmap>(11)
    private val hqHamScared1 = arrayOfNulls<Bitmap>(11)
    private val hqHamScared2 = arrayOfNulls<Bitmap>(11)

    private var hqHamsterBigNormalBitmap: Bitmap? = null
    private var hqHamsterBigNearBitmap: Bitmap? = null

    private var bitmapsLoaded = false
    private var currentLevel = 1
    private var currentDelay = 200L
    private var levelUpMessageTimeLeft = 0L

    var onGameOverCallback: ((score: Int, hamsters: Int, time: Long, hitSelf: Boolean, hitWall: Boolean, auto: Boolean, isWin: Boolean) -> Unit)? = null
    var onLevelUpCallback: ((Int) -> Unit)? = null
    var onEatCallback: (() -> Unit)? = null

    private var startX = 0f
    private var startY = 0f

    private var tLvl = "Lvl"
    private var tPts = "Pts"
    private var tAuto = "AUTO"
    private var tPause = "PAUSA"
    private var tInf = "Infinito"
    private var tSurv = "Survival"
    private var tTapToReturn = "Toque na tela para voltar..."

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    private val paintBodyDark = Paint().apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val paintBodyLight = Paint().apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val paintEye = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintHamsterMain = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintHamsterPink = Paint().apply {
        color = Color.parseColor("#F48FB1")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintHamsterSec = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintBigHamsterGlow = Paint().apply {
        color = Color.parseColor("#FFD600")
        style = Paint.Style.FILL
        isAntiAlias = true
        setShadowLayer(25f, 0f, 0f, Color.parseColor("#FFEA00"))
    }

    private val paintInnerWall = Paint().apply {
        color = Color.parseColor("#8D6E63")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintInnerWallBorder = Paint().apply {
        color = Color.parseColor("#4E342E")
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val paintTimerBar = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintBlood = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintFootprint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
    }

    private val paintText = Paint().apply {
        color = Color.WHITE
        textSize = 60f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("casual", Typeface.NORMAL)
        isAntiAlias = true
        setShadowLayer(5f, 2f, 2f, Color.BLACK)
    }

    private val paintTextBlink = Paint().apply {
        color = Color.WHITE
        textSize = 50f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("casual", Typeface.BOLD)
        isAntiAlias = true
        setShadowLayer(10f, 0f, 0f, Color.BLACK)
    }

    private val paintLevelUp = Paint().apply {
        color = Color.parseColor("#FFEB3B")
        textSize = 90f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("casual", Typeface.BOLD)
        setShadowLayer(15f, 0f, 0f, Color.BLACK)
        isAntiAlias = true
    }

    private val paintWall = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeWidth = 8f
    }

    private val snakePath = Path()

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!isGameOver && !isPaused) {

                if (isSurvivalMode) {
                    if (isAutoPlay) {
                        doSmartHamsterAutoPlayMove()
                    }
                    doSurvivalTick()
                } else {
                    if (isAutoPlay || isAttractMode) {
                        doSmartAutoPlayMove()
                    }

                    moveSnake()

                    if (foodRespawnTimer > 0) {
                        foodRespawnTimer -= currentDelay
                        if (foodRespawnTimer <= 0) {
                            spawnFood()
                        }
                    } else {
                        // Hamster 35% mais lento que a Cobra, aumentando dinamicamente!
                        hamsterMoveAccumulator += 0.65f
                        if (hamsterMoveAccumulator >= 1f) {
                            hamsterMoveAccumulator -= 1f
                            moveHamsterAI()
                        }
                    }

                    if (bigFood != null) {
                        bigFoodTimeLeft -= currentDelay
                        if (bigFoodTimeLeft <= 0) {
                            bigFood = null
                        }
                    }

                    if (levelUpMessageTimeLeft > 0) {
                        levelUpMessageTimeLeft -= currentDelay
                    }
                }

                invalidate()
                postDelayed(this, currentDelay)

            } else if (isPaused) {
                invalidate()
                postDelayed(this, 100)
            }
        }
    }

    fun setLangTexts(l: String, p: String, a: String, pa: String, inf: String, surv: String) {
        tLvl = l
        tPts = p
        tAuto = a
        tPause = pa
        tInf = inf
        tSurv = surv
        tTapToReturn = if (tInf == "Infinite") "Tap the screen to return..." else "Toque na tela para voltar..."
    }

    fun stopGame() {
        removeCallbacks(gameLoop)
        isGameOver = true
        isPaused = false
        snake.clear()
        food = Point(-100, -100)
        bigFood = null
        bloodParticles.clear()
        footprints.clear()
        internalWalls.clear()
        invalidate()
    }

    fun startGame(touchConfig: Boolean, wallsConfig: Boolean, autoConfig: Boolean, infiniteMode: Boolean, survivalMode: Boolean, sSnake: Int, sBonusFood: Int, sField: Int, hqConfig: Boolean, bloodConfig: Boolean, footprintsConfig: Boolean, diagonalConfig: Boolean) {
        stopGame()

        isAttractMode = false
        useTouch = touchConfig
        useInfiniteWalls = !wallsConfig
        isAutoPlay = autoConfig
        isInfiniteMode = infiniteMode
        isSurvivalMode = survivalMode

        isHighQuality = hqConfig
        isBloodEnabled = if (isInfiniteMode) true else bloodConfig
        isFootprintsEnabled = if (isInfiniteMode) true else footprintsConfig
        isDiagonalEnabled = diagonalConfig

        currentSnakeSkin = if (isSurvivalMode) Random.nextInt(0, 14) else sSnake
        currentFieldSkin = if (isSurvivalMode) Random.nextInt(0, 5) else sField
        currentBonusFoodClass = sBonusFood

        if (isSurvivalMode) {
            useInfiniteWalls = false
            currentDelay = 120L
        }

        applySkins(currentSnakeSkin, currentFieldSkin)
        post { resetGameVariables() }
    }

    fun startAttractMode(hqConfig: Boolean, bloodConfig: Boolean, footprintsConfig: Boolean, diagonalConfig: Boolean) {
        stopGame()

        isAttractMode = true
        useTouch = false
        useInfiniteWalls = false
        isAutoPlay = true
        isInfiniteMode = false
        isSurvivalMode = false
        isHighQuality = hqConfig
        isBloodEnabled = bloodConfig
        isFootprintsEnabled = footprintsConfig
        isDiagonalEnabled = diagonalConfig

        currentSnakeSkin = -1
        currentBonusFoodClass = 0
        currentFieldSkin = 0

        post { resetGameVariables() }
    }

    private fun resetGameVariables() {
        if (!isSurvivalMode) {
            currentLevel = 1
            currentDelay = 200L
        }

        levelUpMessageTimeLeft = 0L
        bitmapsLoaded = false
        hamsterAngle = 0f
        hamsterMoveAccumulator = 0f
        survivalSnakeMoveAccumulator = 0f
        foodRespawnTimer = 0L

        bloodParticles.clear()
        footprints.clear()
        internalWalls.clear()
        infiniteWallStages = 0

        snake = mutableListOf(Point(10, 10), Point(9, 10), Point(8, 10))
        direction = Direction.RIGHT
        hamsterDirection = Direction.UP

        totalScore = 0
        hamstersEatenThisGame = 0
        bigFood = null
        eatTimestamps.clear()
        achievedFastEat = false
        startTimeMs = System.currentTimeMillis()

        isGameOver = false
        isPaused = false

        if (isSurvivalMode) {
            hamsterPos = Point(gridSize / 2, gridH / 2 + 5)
            currentFoodSkinId = if (currentBonusFoodClass == 0) Random.nextInt(0, 5) else currentBonusFoodClass + 4
            spawnCherry(gridH)
        } else {
            spawnFood()
        }

        removeCallbacks(gameLoop)
        postDelayed(gameLoop, currentDelay)
    }

    private fun loadHQBitmaps(blockSize: Int) {
        if (blockSize <= 0) return
        try {
            hqHeadBitmap = null
            hqBodyBitmap = null
            hqTailBitmap = null
            hqCurveBitmap = null
            hqFieldBitmap = null
            hqFootprintBitmap = null
            hqFootprint2Bitmap = null
            hqCherryBitmap = null
            hqHamsterBigNormalBitmap = null
            hqHamsterBigNearBitmap = null

            for (i in 0..10) {
                hqHamNormal1[i] = null
                hqHamNormal2[i] = null
                hqHamScared1[i] = null
                hqHamScared2[i] = null
            }

            val sBS = (blockSize * 1.15f).toInt()

            val pS = if (isAttractMode) "coral" else when (currentSnakeSkin) {
                1 -> "blue"
                2 -> "red"
                3 -> "yellow"
                4 -> "black"
                5 -> "coral"
                6 -> "rattlesnake"
                7 -> "tigersnake"
                8 -> "bungarus"
                9 -> "taipan"
                10 -> "xenodermus"
                11 -> "sucuri"
                12 -> "surucucu"
                13 -> "titanoboa"
                else -> ""
            }

            val hN = if (isAttractMode || currentSnakeSkin >= 5) "hq_${pS}head" else "hq_head$pS"
            val bN = if (isAttractMode || currentSnakeSkin >= 5) "hq_${pS}body" else "hq_body$pS"
            val tN = if (isAttractMode || currentSnakeSkin >= 5) "hq_${pS}tail" else "hq_tail$pS"
            val cN = if (isAttractMode || currentSnakeSkin >= 5) "hq_${pS}curve" else "hq_curve$pS"

            hqHeadBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, resources.getIdentifier(hN, "drawable", context.packageName)), sBS, sBS, true)
            hqBodyBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, resources.getIdentifier(bN, "drawable", context.packageName)), sBS, sBS, true)
            hqTailBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, resources.getIdentifier(tN, "drawable", context.packageName)), sBS, sBS, true)
            hqCurveBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, resources.getIdentifier(cN, "drawable", context.packageName)), sBS, sBS, true)

            val fieldNames = arrayOf("fieldgrass", "fieldsand", "fieldstones", "fieldmud", "fieldsnow")
            val fRes = resources.getIdentifier(fieldNames[currentFieldSkin.coerceIn(0, 4)], "drawable", context.packageName)
            if (fRes != 0) {
                hqFieldBitmap = BitmapFactory.decodeResource(resources, fRes)
            }

            val bigName = if (isInfiniteMode || isAttractMode) "rabbit" else "hambig"
            val bigSize = (blockSize * 1.5f).toInt()
            hqHamsterBigNormalBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, resources.getIdentifier("${bigName}01", "drawable", context.packageName)), bigSize, bigSize, true)
            hqHamsterBigNearBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, resources.getIdentifier("${bigName}02", "drawable", context.packageName)), bigSize, bigSize, true)

            val fBs = arrayOf("hampadrao", "hambrown", "hampink", "hamvioleta", "hamblack", "guineapig", "gerbil", "twister", "chinchilla", "mouse", "pygmy")
            val normalHamSize = (blockSize * 1.25f).toInt()

            for (i in 0..10) {
                val p = if (isAttractMode) "hamsnow" else fBs[i]
                hqHamNormal1[i] = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, resources.getIdentifier("${p}01", "drawable", context.packageName)), normalHamSize, normalHamSize, true)
                hqHamNormal2[i] = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, resources.getIdentifier("${p}02", "drawable", context.packageName)), normalHamSize, normalHamSize, true)
                hqHamScared1[i] = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, resources.getIdentifier("${p}03", "drawable", context.packageName)), normalHamSize, normalHamSize, true)
                hqHamScared2[i] = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, resources.getIdentifier("${p}04", "drawable", context.packageName)), normalHamSize, normalHamSize, true)
            }

            hqFootprintBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, resources.getIdentifier("footprints", "drawable", context.packageName)), blockSize, blockSize, true)
            hqFootprint2Bitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, resources.getIdentifier("footprints02", "drawable", context.packageName)), blockSize, blockSize, true)

            val chRes = resources.getIdentifier("cherryfruit", "drawable", context.packageName)
            if (chRes != 0) {
                hqCherryBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(resources, chRes), normalHamSize, normalHamSize, true)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        bitmapsLoaded = true
    }

    private fun applySkins(snakeSkin: Int, fieldSkin: Int) {
        val sC = arrayOf(
            Pair("#2E7D32", "#66BB6A"), Pair("#1565C0", "#42A5F5"), Pair("#C62828", "#EF5350"),
            Pair("#F9A825", "#FFEE58"), Pair("#212121", "#757575"), Pair("#D84315", "#FF7043"),
            Pair("#5D4037", "#8D6E63"), Pair("#FFB300", "#FFE082"), Pair("#FFEB3B", "#FFF59D"),
            Pair("#795548", "#A1887F"), Pair("#455A64", "#78909C"), Pair("#33691E", "#689F38"),
            Pair("#BF360C", "#FF8A65"), Pair("#3E2723", "#A1887F")
        )
        val idx = if (snakeSkin < sC.size && snakeSkin >= 0) snakeSkin else 0
        paintBodyDark.color = Color.parseColor(sC[idx].first)
        paintBodyLight.color = Color.parseColor(sC[idx].second)

        paintText.color = if (fieldSkin == 1 || fieldSkin == 4) Color.BLACK else Color.WHITE
        paintTextBlink.color = paintText.color
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val blockSize = if (width > 0) width / gridSize else 0
        if (blockSize <= 0) return
        if (isHighQuality && !bitmapsLoaded) {
            loadHQBitmaps(blockSize)
        }

        if (hqFieldBitmap != null && isHighQuality) {
            canvas.drawBitmap(hqFieldBitmap!!, null, Rect(0, 0, width, height), null)
        } else {
            val fCols = arrayOf("#1B1B1B", "#FFFFFF", "#9E9E9E", "#FFCC80", "#B2EBF2")
            canvas.drawColor(Color.parseColor(fCols[currentFieldSkin.coerceIn(0, 4)]))
        }

        if (!useInfiniteWalls) {
            val gH = gridH
            val rE = (gridSize * blockSize).toFloat()
            val bE = (gH * blockSize).toFloat()
            val hT = paintWall.strokeWidth / 2f
            canvas.drawRect(hT, hT, rE - hT, bE - hT, paintWall)
        }

        drawInternalWalls(canvas, blockSize)
        drawFootprints(canvas, blockSize, blockSize / 2f)

        if (isSurvivalMode) {
            drawSurvivalCherry(canvas, blockSize, blockSize / 2f)
            drawSurvivalHamster(canvas, blockSize, blockSize / 2f)
        } else {
            if (food.x >= 0) {
                drawHamsterLogic(canvas, blockSize, blockSize / 2f)
            }
        }

        drawBloodParticles(canvas, blockSize, blockSize / 2f)

        if (snake.isNotEmpty()) {
            if (isHighQuality && hqHeadBitmap != null) {
                drawHQSnake(canvas, blockSize, blockSize / 2f)
            } else {
                drawVectorSnake(canvas, blockSize, blockSize / 2f)
            }
        }

        if (bigFood != null && !isAttractMode && !isSurvivalMode) {
            val barW = width * (bigFoodTimeLeft.toFloat() / maxBigFoodTime.toFloat())
            paintTimerBar.color = if (bigFoodTimeLeft > 2000L) Color.parseColor("#4CAF50") else Color.parseColor("#FF5252")
            canvas.drawRect(0f, height - 15f, barW, height.toFloat(), paintTimerBar)
        }

        if (isAttractMode) {
            paintText.textAlign = Paint.Align.CENTER
            canvas.drawText("$tPts: $totalScore", width / 2f, 100f, paintText)
            paintTextBlink.alpha = (abs(Math.sin(System.currentTimeMillis() / 400.0)) * 255).toInt()
            canvas.drawText(tTapToReturn, width / 2f, height / 2f + 150f, paintTextBlink)
        } else {
            val sL = if (isInfiniteMode) tInf else if (isSurvivalMode) tSurv else "$tLvl $currentLevel"
            canvas.drawText("$sL | $tPts: $totalScore", 40f, 100f, paintText)
            if (isAutoPlay) {
                paintText.textAlign = Paint.Align.RIGHT
                canvas.drawText(tAuto, width - 40f, 100f, paintText)
                paintText.textAlign = Paint.Align.LEFT
            }
        }

        if (isPaused) {
            paintText.textAlign = Paint.Align.CENTER
            paintText.textSize = 80f
            canvas.drawText(tPause, width / 2f, height / 2f, paintText)
            paintText.textSize = 60f
            paintText.textAlign = Paint.Align.LEFT
        }

        if (levelUpMessageTimeLeft > 0 && !isAttractMode && !isInfiniteMode && !isSurvivalMode) {
            if ((levelUpMessageTimeLeft / 200) % 2 == 0L) {
                canvas.drawText("NOVO NÍVEL!", width / 2f, height / 2f, paintLevelUp)
            }
        }

        if (!isPaused && !isGameOver) {
            invalidate()
        }
    }

    private fun doSurvivalTick() {
        val gH = gridH

        // Move Player (Hamster)
        var nHX = hamsterPos.x
        var nHY = hamsterPos.y
        when (hamsterDirection) {
            Direction.UP -> nHY--
            Direction.DOWN -> nHY++
            Direction.LEFT -> nHX--
            Direction.RIGHT -> nHX++
        }

        if (nHX in 0 until gridSize && nHY in 0 until gH && !internalWalls.any { it.x == nHX && it.y == nHY }) {
            hamsterPos = Point(nHX, nHY)
            if (isFootprintsEnabled) {
                val fType = if (currentBonusFoodClass >= 5) 2 else 1
                footprints.add(Footprint(hamsterPos.x.toFloat(), hamsterPos.y.toFloat(), hamsterAngle, System.currentTimeMillis(), fType))
            }
            hamsterAngle = when(hamsterDirection) {
                Direction.UP -> -90f
                Direction.DOWN -> 90f
                Direction.LEFT -> 180f
                Direction.RIGHT -> 0f
            }
        }

        // Hamster come a Cherry
        if (hamsterPos.x == cherryPos.x && hamsterPos.y == cherryPos.y) {
            totalScore++
            hamstersEatenThisGame++

            if (!isAutoPlay) {
                val pfx = "_surv"
                val prefs = context.getSharedPreferences("SnakePrefs", Context.MODE_PRIVATE)
                val totalH = prefs.getInt("stat${pfx}_total_hamsters", 0) + hamstersEatenThisGame
                val timeA = System.currentTimeMillis() - startTimeMs
                val maxH = maxOf(prefs.getInt("stat${pfx}_max_hamsters", 0), hamstersEatenThisGame)
                val maxT = maxOf(prefs.getLong("stat${pfx}_max_time", 0L), timeA)

                prefs.edit().putInt("stat${pfx}_total_hamsters", totalH).putLong("stat${pfx}_total_time", timeA).putInt("stat${pfx}_max_hamsters", maxH).putLong("stat${pfx}_max_time", maxT).apply()

                if (hamstersEatenThisGame == 25) {
                    val mAct = context as? MainActivity
                    mAct?.runOnUiThread {
                        val m1 = MainActivity::class.java.getDeclaredMethod("unlockAchievement", Int::class.java)
                        m1.isAccessible = true
                        m1.invoke(mAct, 15)
                    }
                }
                if (hamstersEatenThisGame == 100) {
                    val mAct = context as? MainActivity
                    mAct?.runOnUiThread {
                        val m1 = MainActivity::class.java.getDeclaredMethod("unlockAchievement", Int::class.java)
                        m1.isAccessible = true
                        m1.invoke(mAct, 16)
                    }
                }
            }

            onEatCallback?.invoke()
            if (totalScore >= 150) {
                triggerGameOver(false, false, true)
                return
            }

            // Muros do modo: SURVIVAL (1% de mapa a cada 15 Cherries, limite 8% [8 estágios])
            val expectedStages = totalScore / 15
            if (expectedStages > infiniteWallStages && infiniteWallStages < 8) {
                infiniteWallStages++
                addIncrementalWalls((gridSize * gH * 0.01).toInt(), gH)
            }
            spawnCherry(gH)
        }

        // IA da Cobra caçando (35% mais LENTA)
        survivalSnakeMoveAccumulator += 0.65f
        if (survivalSnakeMoveAccumulator >= 1f) {
            survivalSnakeMoveAccumulator -= 1f

            doSurvivalSnakeAI()
            val nS = getNP(snake[0], direction, gH)

            if (nS.x !in 0 until gridSize || nS.y !in 0 until gH || snake.dropLast(1).any { it.x == nS.x && it.y == nS.y } || internalWalls.any { it.x == nS.x && it.y == nS.y }) {
                triggerGameOver(false, true, true)
                return
            }

            snake.add(0, nS)

            // Cobra come a Cherry
            var snakeAte = false
            if (nS.x == cherryPos.x && nS.y == cherryPos.y) {
                snakeAte = true
                snake.add(Point(snake.last().x, snake.last().y))
                spawnCherry(gH)
            }
            if (!snakeAte) {
                snake.removeAt(snake.size - 1)
            }

            if ((nS.x == hamsterPos.x && nS.y == hamsterPos.y) || snake.any { it.x == hamsterPos.x && it.y == hamsterPos.y }) {
                if (isBloodEnabled) {
                    spawnBlood(hamsterPos.x.toFloat(), hamsterPos.y.toFloat())
                }
                triggerGameOver(false, false, false)
                return
            }
        } else {
            if (snake.any { it.x == hamsterPos.x && it.y == hamsterPos.y }) {
                if (isBloodEnabled) {
                    spawnBlood(hamsterPos.x.toFloat(), hamsterPos.y.toFloat())
                }
                triggerGameOver(false, false, false)
                return
            }
        }
    }

    private fun doSurvivalSnakeAI() {
        val gH = gridH
        val dirToHamster = getNextDirInPath(snake[0], hamsterPos, gH)
        val dirToCherry = getNextDirInPath(snake[0], cherryPos, gH)

        val distH = abs(snake[0].x - hamsterPos.x) + abs(snake[0].y - hamsterPos.y)
        val distC = abs(snake[0].x - cherryPos.x) + abs(snake[0].y - cherryPos.y)

        direction = if (dirToHamster != null && dirToCherry != null) {
            if (distH <= distC) dirToHamster else dirToCherry
        } else if (dirToHamster != null) {
            dirToHamster
        } else if (dirToCherry != null) {
            dirToCherry
        } else {
            Direction.values().firstOrNull { isValidForSnake(getNP(snake[0], it, gH), gH) } ?: direction
        }
    }

    private fun getNextDirInPath(start: Point, target: Point, gH: Int): Direction? {
        val q = mutableListOf<Pair<Point, Direction?>>()
        val visited = Array(gridSize) { BooleanArray(gH) }

        snake.forEach { visited[it.x][it.y] = true }
        visited[start.x][start.y] = true

        for (d in Direction.values()) {
            val np = getNP(start, d, gH)
            if (isValidForSnake(np, gH) && !visited[np.x][np.y]) {
                if (np.x == target.x && np.y == target.y) return d
                visited[np.x][np.y] = true
                q.add(Pair(np, d))
            }
        }

        var head = 0
        while (head < q.size) {
            val curr = q[head++]
            val cp = curr.first
            val firstDir = curr.second

            for (d in Direction.values()) {
                val np = getNP(cp, d, gH)
                if (isValidForSnake(np, gH) && !visited[np.x][np.y]) {
                    if (np.x == target.x && np.y == target.y) return firstDir
                    visited[np.x][np.y] = true
                    q.add(Pair(np, firstDir))
                }
            }
        }
        return null
    }

    private fun isValidForSnake(p: Point, gH: Int): Boolean {
        if (p.x !in 0 until gridSize || p.y !in 0 until gH) return false
        if (internalWalls.any { it.x == p.x && it.y == p.y }) return false
        if (snake.dropLast(1).any { it.x == p.x && it.y == p.y }) return false
        return true
    }

    private fun spawnCherry(gH: Int) {
        var nC: Point
        do {
            nC = Point(Random.nextInt(1, gridSize - 1), Random.nextInt(1, gH - 1))
        } while (snake.any { it.x == nC.x && it.y == nC.y } || internalWalls.any { it.x == nC.x && it.y == nC.y } || (nC.x == hamsterPos.x && nC.y == hamsterPos.y))

        cherryPos = nC
    }

    private fun drawSurvivalHamster(canvas: Canvas, b: Int, hB: Float) {
        val anim = (System.currentTimeMillis() / 200) % 2 == 0L
        val fX = hamsterPos.x.toFloat()
        val fY = hamsterPos.y.toFloat()

        canvas.save()
        canvas.rotate(hamsterAngle + 90f, fX * b + hB, fY * b + hB)

        val isNear = if (snake.isNotEmpty()) sqrt(((fX - snake[0].x) * (fX - snake[0].x) + (fY - snake[0].y) * (fY - snake[0].y)).toDouble()) < 8.0 else false

        if (isHighQuality) {
            val img = if (isNear) {
                if (anim) hqHamScared1[currentFoodSkinId] else hqHamScared2[currentFoodSkinId]
            } else {
                if (anim) hqHamNormal1[currentFoodSkinId] else hqHamNormal2[currentFoodSkinId]
            }
            if (img != null) {
                canvas.drawBitmap(img, fX * b - (b * 0.125f), fY * b - (b * 0.125f), null)
            } else {
                drawVectorHamster(canvas, fX, fY, b, hB, 1.25f)
            }
        } else {
            drawVectorHamster(canvas, fX, fY, b, hB, 1.25f)
        }
        canvas.restore()
    }

    private fun drawSurvivalCherry(canvas: Canvas, b: Int, hB: Float) {
        val fX = cherryPos.x.toFloat()
        val fY = cherryPos.y.toFloat()

        if (isHighQuality && hqCherryBitmap != null) {
            canvas.drawBitmap(hqCherryBitmap!!, fX * b - (b * 0.125f), fY * b - (b * 0.125f), null)
        } else {
            val cX = fX * b + hB
            val cY = fY * b + hB
            paintHamsterMain.color = Color.RED
            canvas.drawCircle(cX, cY, hB * 0.6f, paintHamsterMain)
            paintHamsterSec.color = Color.GREEN
            canvas.drawRect(cX - 2f, cY - hB, cX + 2f, cY, paintHamsterSec)
        }
    }

    private fun drawFootprints(canvas: Canvas, blockSize: Int, hB: Float) {
        if (!isFootprintsEnabled) return
        val now = System.currentTimeMillis()
        val it = footprints.iterator()

        while (it.hasNext()) {
            val fp = it.next()
            val age = now - fp.spawnTime
            if (age > 1500L) {
                it.remove()
            } else {
                val bmp = if (fp.type == 2) hqFootprint2Bitmap else hqFootprintBitmap
                if (bmp != null) {
                    paintFootprint.alpha = (255 - (age * 255L / 1500L)).toInt().coerceIn(0, 255)
                    canvas.save()
                    canvas.rotate(fp.angle + 90f, fp.x * blockSize + hB, fp.y * blockSize + hB)
                    canvas.drawBitmap(bmp, fp.x * blockSize, fp.y * blockSize, paintFootprint)
                    canvas.restore()
                }
            }
        }
    }

    private fun drawInternalWalls(canvas: Canvas, b: Int) {
        for (w in internalWalls) {
            val l = w.x * b.toFloat()
            val t = w.y * b.toFloat()
            canvas.drawRect(l, t, l + b, t + b, paintInnerWall)
            val m = b * 0.1f
            canvas.drawRect(l + m, t + m, l + b - m, t + b - m, paintInnerWallBorder)
        }
    }

    private fun generateInternalWalls(level: Int) {
        internalWalls.clear()
        if (level == 1) return
        val gH = gridH
        // MUROS PADRÃO: Level 2 = 4.5% / Level 3 = 6.5%
        addIncrementalWalls((gridSize * gH * (if (level == 2) 0.045 else 0.065)).toInt(), gH)
    }

    private fun addIncrementalWalls(target: Int, gH: Int) {
        var count = 0
        var att = 0
        while (count < target && att < 1000) {
            att++
            val len = Random.nextInt(3, 6)
            var curP = Point(Random.nextInt(1, gridSize - 1), Random.nextInt(1, gH - 1))
            val temp = mutableListOf<Point>()

            for (i in 0 until len) {
                if (isSafeForWall(curP) && !temp.any { it.x == curP.x && it.y == curP.y }) {
                    temp.add(Point(curP.x, curP.y))
                } else {
                    break
                }
                val moves = listOf(Point(0, -1), Point(0, 1), Point(-1, 0), Point(1, 0)).shuffled()
                var moved = false
                for (m in moves) {
                    val nP = Point(curP.x + m.x, curP.y + m.y)
                    if (nP.x in 1 until gridSize - 1 && nP.y in 1 until gH - 1) {
                        curP = nP
                        moved = true
                        break
                    }
                }
                if (!moved) break
            }
            if (temp.size >= 3) {
                internalWalls.addAll(temp)
                count += temp.size
            }
        }
    }

    private fun isSafeForWall(p: Point): Boolean {
        if (snake.isEmpty()) return true
        val head = snake[0]
        if (abs(p.x - head.x) + abs(p.y - head.y) < 6) return false
        return !snake.any { it.x == p.x && it.y == p.y } && !(food.x == p.x && food.y == p.y) && !(bigFood?.x == p.x && bigFood?.y == p.y) && !internalWalls.any { it.x == p.x && it.y == p.y }
    }

    private fun drawBloodParticles(canvas: Canvas, b: Int, hB: Float) {
        val it = bloodParticles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx
            p.y += p.vy
            p.life -= 1f / p.maxLife
            if (p.life <= 0f) {
                it.remove()
            } else {
                paintBlood.color = p.color
                paintBlood.alpha = (p.life * 255).toInt().coerceIn(0, 255)
                canvas.drawCircle(p.x * b + hB, p.y * b + hB, p.radius * hB, paintBlood)
            }
        }
    }

    private fun drawHamsterLogic(canvas: Canvas, b: Int, hB: Float) {
        val anim = (System.currentTimeMillis() / 200) % 2 == 0L
        var fX = food.x.toFloat()
        var fY = food.y.toFloat()

        if (lastFoodMoveTime > 0 && !isPaused) {
            val prog = ((System.currentTimeMillis() - lastFoodMoveTime) / (currentDelay * 4.0f)).coerceIn(0f, 1f)
            fX = previousFood.x + (food.x - previousFood.x) * prog
            fY = previousFood.y + (food.y - previousFood.y) * prog
        }

        canvas.save()
        canvas.rotate(hamsterAngle + 90f, fX * b + hB, fY * b + hB)

        val isNear = if (snake.isNotEmpty()) sqrt(((fX - snake[0].x) * (fX - snake[0].x) + (fY - snake[0].y) * (fY - snake[0].y)).toDouble()) < 8.0 else false

        if (isHighQuality) {
            val img = if (isNear) {
                if (anim) hqHamScared1[currentFoodSkinId] else hqHamScared2[currentFoodSkinId]
            } else {
                if (anim) hqHamNormal1[currentFoodSkinId] else hqHamNormal2[currentFoodSkinId]
            }
            if (img != null) {
                canvas.drawBitmap(img, fX * b - (b * 0.125f), fY * b - (b * 0.125f), null)
            } else {
                drawVectorHamster(canvas, fX, fY, b, hB, 1.25f)
            }
        } else {
            drawVectorHamster(canvas, fX, fY, b, hB, 1.25f)
        }
        canvas.restore()

        bigFood?.let { bf ->
            val isNearBig = if (snake.isNotEmpty()) sqrt(((bf.x - snake[0].x) * (bf.x - snake[0].x) + (bf.y - snake[0].y) * (bf.y - snake[0].y)).toDouble()) < 4.0 else false

            if (isHighQuality && hqHamsterBigNormalBitmap != null) {
                val img = if (isNearBig) hqHamsterBigNearBitmap!! else hqHamsterBigNormalBitmap!!
                canvas.drawBitmap(img, bf.x * b.toFloat() - (b * 0.25f), bf.y * b.toFloat() - (b * 0.25f), null)
            } else {
                drawVectorHamster(canvas, bf.x.toFloat(), bf.y.toFloat(), b, hB, 1.5f)
            }
        }
    }

    private fun drawHQSnake(canvas: Canvas, b: Int, hB: Float) {
        val off = (b * 0.15f) / 2f
        for (i in snake.indices) {
            val cur = snake[i]
            val cX = cur.x * b.toFloat() + hB
            val cY = cur.y * b.toFloat() + hB
            val left = cur.x * b.toFloat() - off
            val top = cur.y * b.toFloat() - off

            canvas.save()

            if (i == 0) {
                val ang = if (snake.size > 1) {
                    getAngleTowards(snake[1], snake[0])
                } else {
                    when(direction) {
                        Direction.UP -> 0f
                        Direction.RIGHT -> 90f
                        Direction.DOWN -> 180f
                        else -> 270f
                    }
                }
                canvas.rotate(ang, cX, cY)
                canvas.drawBitmap(hqHeadBitmap!!, left, top, null)

            } else if (i == snake.size - 1) {
                val prev = snake[i - 1]
                canvas.rotate(getAngleTowards(cur, prev), cX, cY)
                canvas.drawBitmap(hqTailBitmap!!, left, top, null)
            } else {
                val prev = snake[i - 1]
                val next = snake[i + 1]
                val dx1 = wrap(prev.x - cur.x)
                val dy1 = wrap(prev.y - cur.y)
                val dx2 = wrap(next.x - cur.x)
                val dy2 = wrap(next.y - cur.y)

                if (dx1 == -dx2 && dy1 == -dy2) {
                    canvas.rotate(getAngleTowards(cur, prev), cX, cY)
                    canvas.drawBitmap(hqBodyBitmap!!, left, top, null)
                } else {
                    canvas.rotate(getCurveAngle(dx1, dy1, dx2, dy2), cX, cY)
                    canvas.drawBitmap(hqCurveBitmap!!, left, top, null)
                }
            }
            canvas.restore()
        }
    }

    private fun drawVectorSnake(canvas: Canvas, b: Int, hB: Float) {
        paintBodyDark.strokeWidth = b * 0.92f
        paintBodyLight.strokeWidth = b * 0.46f
        snakePath.reset()
        val sX = (snake[0].x * b) + hB
        val sY = (snake[0].y * b) + hB
        snakePath.moveTo(sX, sY)
        val tA = System.currentTimeMillis() / 150.0

        for (i in 1 until snake.size) {
            val c = snake[i]
            val p = snake[i - 1]
            var dx = p.x - c.x
            var dy = p.y - c.y
            if (abs(dx) > 1 || abs(dy) > 1) {
                snakePath.moveTo((c.x * b) + hB, (c.y * b) + hB)
                continue
            }
            val w = (kotlin.math.sin(tA - i * 0.6) * (b * 0.18f)).toFloat()
            snakePath.lineTo((c.x * b) + hB + (-dy * w), (c.y * b) + hB + (dx * w))
        }
        canvas.drawPath(snakePath, paintBodyDark)
        canvas.drawPath(snakePath, paintBodyLight)
    }

    private fun wrap(v: Int): Int = if (v > 1) -1 else if (v < -1) 1 else v

    private fun getAngleTowards(p1: Point, p2: Point): Float {
        val dx = wrap(p2.x - p1.x)
        val dy = wrap(p2.y - p1.y)
        return if (dx == 1) 90f else if (dx == -1) 270f else if (dy == 1) 180f else 0f
    }

    private fun getCurveAngle(dx1: Int, dy1: Int, dx2: Int, dy2: Int): Float {
        val top = (dy1 == -1 || dy2 == -1)
        val right = (dx1 == 1 || dx2 == 1)
        val bot = (dy1 == 1 || dy2 == 1)
        val left = (dx1 == -1 || dx2 == -1)
        return if (top && right) 0f else if (right && bot) 90f else if (bot && left) 180f else 270f
    }

    private fun drawVectorHamster(canvas: Canvas, fX: Float, fY: Float, b: Int, hB: Float, s: Float) {
        val hX = fX * b + hB
        val hY = fY * b + hB
        val hS = hB * s
        canvas.drawCircle(hX, hY, hS, paintHamsterMain)
    }

    private fun doSmartHamsterAutoPlayMove() {
        val gH = gridH
        var bestDir = hamsterDirection
        var bestScore = -Double.MAX_VALUE

        for (dir in Direction.values()) {
            var nx = hamsterPos.x
            var ny = hamsterPos.y
            when(dir) {
                Direction.UP -> ny--
                Direction.DOWN -> ny++
                Direction.LEFT -> nx--
                Direction.RIGHT -> nx++
            }

            if (nx !in 0 until gridSize || ny !in 0 until gH || internalWalls.any { it.x == nx && it.y == ny }) {
                continue
            }

            var minBodyDist = Double.MAX_VALUE
            for (part in snake) {
                val d = sqrt(((nx - part.x) * (nx - part.x) + (ny - part.y) * (ny - part.y)).toDouble())
                if (d < minBodyDist) minBodyDist = d
            }

            val distToCherry = sqrt(((nx - cherryPos.x) * (nx - cherryPos.x) + (ny - cherryPos.y) * (ny - cherryPos.y)).toDouble())

            var score = 0.0

            if (minBodyDist <= 3.0) {
                score -= 10000.0 / minBodyDist
            }

            score -= distToCherry

            if (score > bestScore) {
                bestScore = score
                bestDir = dir
            }
        }
        hamsterDirection = bestDir
    }

    private fun moveHamsterAI() {
        if (food.x < 0) return

        val head = snake[0]
        val gH = gridH
        val moves = mutableListOf(Point(0, -1), Point(0, 1), Point(-1, 0), Point(1, 0))

        if (isDiagonalEnabled && !isSurvivalMode) {
            moves.addAll(listOf(Point(-1, -1), Point(1, -1), Point(-1, 1), Point(1, 1)))
        }

        val valid = moves.map { Point(food.x + it.x, food.y + it.y) }.filter {
            it.x in 1 until gridSize - 1 && it.y in 1 until gH - 1 && !snake.any { s -> s.x == it.x && s.y == it.y } && !internalWalls.any { w -> w.x == it.x && w.y == it.y }
        }

        if (valid.isNotEmpty()) {
            val isNear = sqrt(((food.x - head.x) * (food.x - head.x) + (food.y - head.y) * (food.y - head.y)).toDouble()) < 8.0
            val target = if (isNear) {
                valid.maxByOrNull { sqrt(((it.x - head.x) * (it.x - head.x) + (it.y - head.y) * (it.y - head.y)).toDouble()) }!!
            } else {
                valid.random()
            }

            if (isFootprintsEnabled) {
                val type = if (currentBonusFoodClass >= 5) 2 else 1
                footprints.add(Footprint(food.x.toFloat(), food.y.toFloat(), hamsterAngle, System.currentTimeMillis(), type))
            }

            previousFood = Point(food.x, food.y)
            hamsterAngle = Math.toDegrees(kotlin.math.atan2((target.y - food.y).toDouble(), (target.x - food.x).toDouble())).toFloat()
            food = target
            lastFoodMoveTime = System.currentTimeMillis()
        }
    }

    private fun doSmartAutoPlayMove() {
        val head = snake[0]
        val target = bigFood ?: food
        val gH = gridH
        val moves = Direction.values().filter { d ->
            val nP = getNP(head, d, gH)
            isValid(nP, gH) && !snake.dropLast(1).any { it.x == nP.x && it.y == nP.y }
        }
        if (moves.isEmpty()) return

        direction = moves.maxByOrNull { d ->
            val nP = getNP(head, d, gH)
            floodFill(nP, gH) * 1000 - (abs(nP.x - target.x) + abs(nP.y - target.y))
        }!!
    }

    private fun floodFill(s: Point, gH: Int): Int {
        val visited = Array(gridSize) { BooleanArray(gH) }
        snake.forEach { visited[it.x][it.y] = true }
        var count = 0
        val q = mutableListOf(s)

        if (s.x !in 0 until gridSize || s.y !in 0 until gH || visited[s.x][s.y]) return 0
        visited[s.x][s.y] = true

        while (q.isNotEmpty()) {
            val c = q.removeAt(0)
            count++
            Direction.values().forEach { d ->
                val n = getNP(c, d, gH)
                if (isValid(n, gH) && !visited[n.x][n.y]) {
                    visited[n.x][n.y] = true
                    q.add(n)
                }
            }
        }
        return count
    }

    private fun getNP(p: Point, d: Direction, gH: Int): Point {
        var nx = p.x
        var ny = p.y
        when(d) {
            Direction.UP -> ny--
            Direction.DOWN -> ny++
            Direction.LEFT -> nx--
            else -> nx++
        }
        if (useInfiniteWalls) {
            if (nx < 0) nx = gridSize - 1 else if (nx >= gridSize) nx = 0
            if (ny < 0) ny = gH - 1 else if (ny >= gH) ny = 0
        }
        return Point(nx, ny)
    }

    private fun isValid(p: Point, gH: Int): Boolean {
        if (!useInfiniteWalls && (p.x !in 0 until gridSize || p.y !in 0 until gH)) return false
        return !internalWalls.any { it.x == p.x && it.y == p.y }
    }

    private fun moveSnake() {
        val gH = gridH
        val nH = getNP(snake[0], direction, gH)

        if (!useInfiniteWalls && (nH.x !in 0 until gridSize || nH.y !in 0 until gH)) {
            triggerGameOver(false, true, false)
            return
        }

        if (snake.dropLast(1).any { it.x == nH.x && it.y == nH.y } || internalWalls.any { it.x == nH.x && it.y == nH.y }) {
            triggerGameOver(nH.x == food.x && nH.y == food.y, true, false)
            return
        }

        snake.add(0, nH)
        var ate = false

        if (!isAutoPlay && !isSurvivalMode && food.x >= 0) {
            val prefs = context.getSharedPreferences("SnakePrefs", Context.MODE_PRIVATE)
            val totalH = prefs.getInt("stat_norm_total_hamsters", 0) + hamstersEatenThisGame + 1

            if (isInfiniteMode) {
                if (totalScore + 10 >= 2500) triggerAchievementInView(13)
                if (totalScore + 10 >= 5000) triggerAchievementInView(14)
            } else {
                if (totalH > 0) triggerAchievementInView(0)
                if (hamstersEatenThisGame + 1 >= 50) triggerAchievementInView(1)
                if (hamstersEatenThisGame + 1 >= 75) triggerAchievementInView(2)
                if (hamstersEatenThisGame + 1 >= 100) triggerAchievementInView(3)
                if (totalH >= 10000) triggerAchievementInView(9)
            }
        }

        if (nH.x == food.x && nH.y == food.y) {
            totalScore += 10
            hamstersEatenThisGame++

            if (isBloodEnabled && !isAttractMode) {
                spawnBlood(food.x.toFloat(), food.y.toFloat())
            }

            food = Point(-100, -100)
            foodRespawnTimer = 1000L

            if (hamstersEatenThisGame % 10 == 0) {
                spawnBigFood(gH)
            }

            ate = true
            onEatCallback?.invoke()
            checkLevel()
        } else if (bigFood != null && nH.x == bigFood!!.x && nH.y == bigFood!!.y) {
            totalScore += 30
            hamstersEatenThisGame++
            bigFood = null

            if (isBloodEnabled && !isAttractMode) {
                repeat(2) { spawnBlood(nH.x.toFloat(), nH.y.toFloat()) }
            }

            ate = true
            onEatCallback?.invoke()
            checkLevel()
        }

        if (!ate) {
            snake.removeAt(snake.size - 1)
        }
    }

    private fun triggerAchievementInView(index: Int) {
        val mAct = context as? MainActivity
        mAct?.runOnUiThread {
            try {
                val method = MainActivity::class.java.getDeclaredMethod("unlockAchievement", Int::class.java)
                method.isAccessible = true
                method.invoke(mAct, index)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun checkLevel() {
        if (isAttractMode) return
        val gH = gridH

        // CÁLCULO DE VELOCIDADE (ESCALA 1 a 10) E MUROS INFINITOS (1% A CADA 500)
        if (isInfiniteMode) {
            currentDelay = (200L - (totalScore / 500) * 15L).coerceAtLeast(50L)

            val expectedInfStages = totalScore / 500
            if (expectedInfStages > infiniteWallStages && infiniteWallStages < 8) {
                infiniteWallStages++
                addIncrementalWalls((gridSize * gH * 0.01).toInt(), gH)
            }
        } else {
            if (currentLevel == 1 && totalScore >= 700) {
                currentLevel = 2
                levelUpMessageTimeLeft = 2500L
                generateInternalWalls(2)
                onLevelUpCallback?.invoke(2)
            } else if (currentLevel == 2 && totalScore >= 1400) {
                currentLevel = 3
                levelUpMessageTimeLeft = 2500L
                generateInternalWalls(3)
                onLevelUpCallback?.invoke(3)
            } else if (currentLevel == 3 && totalScore >= 2000) {
                isGameOver = true
                onLevelUpCallback?.invoke(4)
            }

            currentDelay = when(currentLevel) {
                1 -> (200L - (totalScore.toFloat() / 700f) * 60L).toLong().coerceIn(140L, 200L)
                2 -> (140L - ((totalScore - 700).toFloat() / 700f) * 45L).toLong().coerceIn(95L, 140L)
                else -> (95L - ((totalScore - 1400).toFloat() / 600f) * 45L).toLong().coerceIn(50L, 95L)
            }
        }
    }

    private fun triggerGameOver(s: Boolean, w: Boolean, isWin: Boolean) {
        if (isAttractMode) {
            startAttractMode(isHighQuality, true, isFootprintsEnabled, isDiagonalEnabled)
        } else {
            isGameOver = true
            onGameOverCallback?.invoke(totalScore, hamstersEatenThisGame, System.currentTimeMillis() - startTimeMs, s, w, isAutoPlay, isWin)
        }
    }

    private fun spawnFood() {
        val gH = gridH
        var nF: Point

        do {
            val side = Random.nextInt(4)
            var nx = Random.nextInt(1, gridSize - 1)
            var ny = Random.nextInt(1, gH - 1)

            when(side) {
                0 -> ny = 1
                1 -> ny = gH - 2
                2 -> nx = 1
                3 -> nx = gridSize - 2
            }

            nF = Point(nx, ny)
        } while (snake.any { it.x == nF.x && it.y == nF.y } || internalWalls.any { it.x == nF.x && it.y == nF.y })

        food = nF
        previousFood = Point(food.x, food.y)
        lastFoodMoveTime = System.currentTimeMillis()

        currentFoodSkinId = if (isAttractMode) 0 else if (currentBonusFoodClass == 0) Random.nextInt(0, 5) else currentBonusFoodClass + 4
        hamsterAngle = Math.toDegrees(kotlin.math.atan2((gH / 2.0 - food.y), (gridSize / 2.0 - food.x))).toFloat()
    }

    private fun spawnBigFood(gH: Int) {
        var nB: Point
        do {
            nB = Point(Random.nextInt(1, gridSize - 1), Random.nextInt(1, gH - 1))
        } while (nB == food || snake.any { it.x == nB.x && it.y == nB.y } || internalWalls.any { it.x == nB.x && it.y == nB.y })

        bigFood = nB
        bigFoodTimeLeft = maxBigFoodTime
        currentBigFoodSkinId = Random.nextInt(0, 5)
    }

    private fun spawnBlood(x: Float, y: Float) {
        val colors = intArrayOf(Color.RED, Color.parseColor("#B71C1C"), Color.parseColor("#E53935"))
        repeat(Random.nextInt(10, 15)) {
            val ang = Random.nextFloat() * 6.28
            val spd = Random.nextFloat() * 0.15f + 0.05f
            bloodParticles.add(BloodParticle(x, y, (Math.cos(ang) * spd).toFloat(), (Math.sin(ang) * spd).toFloat(), 1f, Random.nextFloat() * 15f + 15f, colors.random(), Random.nextFloat() * 0.3f + 0.1f))
        }
    }

    fun setDirection(d: Direction) {
        if (isSurvivalMode) {
            hamsterDirection = d
        } else {
            if ((d == Direction.UP && direction != Direction.DOWN) ||
                (d == Direction.DOWN && direction != Direction.UP) ||
                (d == Direction.LEFT && direction != Direction.RIGHT) ||
                (d == Direction.RIGHT && direction != Direction.LEFT)) {
                direction = d
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (isAttractMode) {
            if (e.action == MotionEvent.ACTION_DOWN) onExitAttractModeCallback?.invoke()
            return true
        }

        if (!useTouch || isPaused || isGameOver || isAutoPlay) return true

        if (e.action == MotionEvent.ACTION_DOWN) {
            startX = e.x
            startY = e.y
        } else if (e.action == MotionEvent.ACTION_UP) {
            val dx = e.x - startX
            val dy = e.y - startY
            if (abs(dx) > 20 || abs(dy) > 20) {
                if (abs(dx) > abs(dy)) {
                    setDirection(if (dx > 0) Direction.RIGHT else Direction.LEFT)
                } else {
                    setDirection(if (dy > 0) Direction.DOWN else Direction.UP)
                }
            }
        }
        return true
    }
}