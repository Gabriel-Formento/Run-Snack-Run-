package com.example.myapplication

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputFilter
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private lateinit var snakeGameView: SnakeGameView
    private lateinit var prefs: android.content.SharedPreferences

    private var bgmPlayer: MediaPlayer? = null
    private var sfxPlayer: MediaPlayer? = null
    private var currentBgmId: Int = -1

    private var isSoundEnabled = true
    private var isBloodEnabled = false
    private var isFootprintsEnabled = true
    private var isDiagonalEnabled = false
    private var musicVolume = 50
    private var currentLang = "pt"

    // Variáveis de abas ativas e estados de controle
    private var currentStatsMode = "norm"
    private var currentRecMode = "norm"
    private var isCountdownActive = false

    // Variáveis do Tutorial
    private var tutorialStep = 0
    private var tutorialFrameIndex = 0
    private val tutorialHandler = Handler(Looper.getMainLooper())
    private lateinit var tutorialRunnable: Runnable

    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { startAttractMode() }

    private var achTitles = arrayOf<String>()
    private var achDesc = arrayOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(R.layout.activity_main)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        prefs = getSharedPreferences("SnakePrefs", Context.MODE_PRIVATE)

        // Definição dos Defaults (Apenas para a primeira execução)
        if (!prefs.contains("first_run_defaults_set")) {
            prefs.edit()
                .putBoolean("useTouch", true)          // Touch
                .putBoolean("useWalls", false)         // Paredes Mortais DESATIVADAS
                .putBoolean("highQuality", true)       // Modo HQ ATIVADO
                .putBoolean("soundEnabled", true)      // Sons ATIVADOS
                .putBoolean("bloodEnabled", false)     // Efeito Sangue DESATIVADO
                .putBoolean("footprintsEnabled", true) // Pegadas ATIVADAS
                .putBoolean("diagonalEnabled", false)  // Diagonal DESATIVADO
                .putInt("musicVolume", 50)             // Volume 50%
                .putBoolean("first_run_defaults_set", true)
                .apply()
        }

        isSoundEnabled = prefs.getBoolean("soundEnabled", true)
        isBloodEnabled = prefs.getBoolean("bloodEnabled", false)
        isFootprintsEnabled = prefs.getBoolean("footprintsEnabled", true)
        isDiagonalEnabled = prefs.getBoolean("diagonalEnabled", false)
        musicVolume = prefs.getInt("musicVolume", 50)
        currentLang = prefs.getString("language", "pt") ?: "pt"

        val popupBg = GradientDrawable().apply {
            setColor(Color.parseColor("#EE1E1E1E"))
            cornerRadius = 48f
            setStroke(3, Color.parseColor("#444444"))
        }

        findViewById<LinearLayout>(R.id.optionsContainer).background = popupBg
        findViewById<LinearLayout>(R.id.statsContainer).background = popupBg
        findViewById<LinearLayout>(R.id.recordsContainer).background = popupBg
        findViewById<LinearLayout>(R.id.achievementsContainer).background = popupBg

        snakeGameView = SnakeGameView(this)
        findViewById<FrameLayout>(R.id.gameContainer).addView(snakeGameView)

        applyLanguage()
        setupMenu()
        setupOptionsAndSkins()
        setupGameControls()

        playSplashScreen()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        resetIdleTimer()
        return super.dispatchTouchEvent(ev)
    }

    private fun resetIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable)
        val menuLayout = findViewById<View>(R.id.menuLayout)
        if (menuLayout.visibility == View.VISIBLE) {
            idleHandler.postDelayed(idleRunnable, 60000L)
        }
    }

    override fun onPause() {
        super.onPause()
        bgmPlayer?.pause()
        sfxPlayer?.pause()

        val gameLayout = findViewById<View>(R.id.gameLayout)
        if (gameLayout.visibility == View.VISIBLE && !snakeGameView.isGameOver && !snakeGameView.isAttractMode && !isCountdownActive) {
            snakeGameView.isPaused = true
            updateIngameTexts()
        }
        idleHandler.removeCallbacks(idleRunnable)
    }

    override fun onResume() {
        super.onResume()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (isSoundEnabled) {
            val gameLayout = findViewById<View>(R.id.gameLayout)
            if (gameLayout.visibility == View.VISIBLE) {
                if (!snakeGameView.isPaused && !snakeGameView.isGameOver) bgmPlayer?.start()
            } else {
                bgmPlayer?.start()
            }
        }
        resetIdleTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        bgmPlayer?.release()
        sfxPlayer?.release()
        idleHandler.removeCallbacks(idleRunnable)
        tutorialHandler.removeCallbacks(tutorialRunnable)
    }

    private fun startAttractMode() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val gameLayout = findViewById<View>(R.id.gameLayout)
        gameLayout.alpha = 0f
        showLayout(R.id.gameLayout)
        gameLayout.animate().alpha(1f).setDuration(1200).start()

        findViewById<View>(R.id.flashView).visibility = View.GONE
        findViewById<ImageView>(R.id.ivGameOver).visibility = View.GONE
        findViewById<LinearLayout>(R.id.bottomControlBar).visibility = View.GONE
        findViewById<View>(R.id.gameDivisor).visibility = View.GONE

        snakeGameView.onExitAttractModeCallback = {
            runOnUiThread {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                snakeGameView.stopGame()
                gameLayout.animate().alpha(0f).setDuration(800).withEndAction {
                    showLayout(R.id.menuLayout)
                    gameLayout.alpha = 1f
                    resetIdleTimer()
                }.start()
            }
        }

        val useHQ = prefs.getBoolean("highQuality", true)
        snakeGameView.startAttractMode(useHQ, true, isFootprintsEnabled, isDiagonalEnabled)
    }

    private fun playBGM(resourceId: Int, isLooping: Boolean = true) {
        if (currentBgmId == resourceId && bgmPlayer?.isPlaying == true) return
        bgmPlayer?.release()
        currentBgmId = resourceId

        if (resourceId != -1 && isSoundEnabled) {
            try {
                bgmPlayer = MediaPlayer.create(this, resourceId)
                bgmPlayer?.isLooping = isLooping
                val vol = musicVolume / 100f
                bgmPlayer?.setVolume(vol, vol)
                bgmPlayer?.start()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun playSFX(resourceId: Int) {
        if (!isSoundEnabled) return
        try {
            sfxPlayer?.release()
            sfxPlayer = MediaPlayer.create(this, resourceId)
            sfxPlayer?.start()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun toggleSound(enabled: Boolean) {
        isSoundEnabled = enabled
        if (!enabled) {
            bgmPlayer?.pause()
            sfxPlayer?.stop()
        } else {
            val gameLayout = findViewById<View>(R.id.gameLayout)
            if (!(gameLayout.visibility == View.VISIBLE && snakeGameView.isPaused)) {
                bgmPlayer?.start()
            }
        }
    }

    private fun vibratePhone() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(800)
            }
        }
    }

    private fun playSplashScreen() {
        val splashLayout = findViewById<FrameLayout?>(R.id.splashLayout)
        val ivSplash = findViewById<ImageView?>(R.id.ivSplash)
        val ivSplashAnim = findViewById<ImageView?>(R.id.ivSplashAnim)
        val progressContainer = findViewById<FrameLayout?>(R.id.progressContainer)
        val vProgressBar = findViewById<View?>(R.id.vProgressBar)

        vProgressBar?.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 12f
        }

        playSFX(R.raw.abertura1)

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                ivSplash?.setImageResource(R.drawable.loadscreen03)
                ivSplashAnim?.visibility = View.VISIBLE
                progressContainer?.visibility = View.VISIBLE

                Handler(Looper.getMainLooper()).postDelayed({
                    playSFX(R.raw.loadscreen2)
                }, 1500)

                val seqFrames = intArrayOf(1, 2, 1, 2, 1, 2, 1, 2, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 13, 14, 15, 16, 13, 14, 15, 16)
                val duration = 7750L
                val frameDelay = 250L

                var frameIndex = 0
                val animHandler = Handler(Looper.getMainLooper())
                val frameRunnable = object : Runnable {
                    override fun run() {
                        if (frameIndex < seqFrames.size) {
                            val numStr = String.format("img%02d", seqFrames[frameIndex])
                            val resId = resources.getIdentifier(numStr, "drawable", packageName)
                            if (resId != 0) ivSplashAnim?.setImageResource(resId)
                            frameIndex++
                            animHandler.postDelayed(this, frameDelay)
                        }
                    }
                }
                animHandler.post(frameRunnable)

                if (progressContainer != null && vProgressBar != null) {
                    val animator = ValueAnimator.ofFloat(0f, 1f)
                    animator.duration = duration
                    animator.addUpdateListener { anim ->
                        val progress = anim.animatedValue as Float
                        val lp = vProgressBar.layoutParams
                        if (lp != null) {
                            lp.width = (progressContainer.width * progress).toInt()
                            vProgressBar.layoutParams = lp
                        }
                    }
                    animator.start()
                }

                animHandler.postDelayed({
                    splashLayout?.animate()?.alpha(0f)?.setDuration(800)?.withEndAction {
                        splashLayout.visibility = View.GONE

                        val isFirstRun = prefs.getBoolean("first_run", true)
                        if (isFirstRun) {
                            setupInitialLanguageSelection()
                        } else {
                            showLayout(R.id.menuLayout)
                            playBGM(R.raw.inicial)
                            resetIdleTimer()
                        }

                    }
                }, duration)

            } catch (e: Exception) {
                e.printStackTrace()
                splashLayout?.visibility = View.GONE
                showLayout(R.id.menuLayout)
                playBGM(R.raw.inicial)
                resetIdleTimer()
            }
        }, 3500)
    }

    private fun setupInitialLanguageSelection() {
        val initialLangLayout = findViewById<FrameLayout>(R.id.initialLangLayout)
        initialLangLayout.visibility = View.VISIBLE

        findViewById<ImageButton>(R.id.btnInitLangPT).setOnClickListener { setInitLang("pt") }
        findViewById<ImageButton>(R.id.btnInitLangEN).setOnClickListener { setInitLang("en") }
        findViewById<ImageButton>(R.id.btnInitLangES).setOnClickListener { setInitLang("es") }
        findViewById<ImageButton>(R.id.btnInitLangJA).setOnClickListener { setInitLang("ja") }
    }

    private fun setInitLang(lang: String) {
        changeLanguage(lang)
        findViewById<FrameLayout>(R.id.initialLangLayout).visibility = View.GONE
        startTutorial()
    }

    private fun startTutorial() {
        val tutLayout = findViewById<FrameLayout>(R.id.tutorialLayout)
        val ivTut = findViewById<ImageView>(R.id.ivTutorial)
        val tvTap = findViewById<TextView>(R.id.tvTutorialTap)

        tutLayout.visibility = View.VISIBLE
        tutLayout.alpha = 1f

        tvTap.text = getStr("tap_to_advance")
        val blinkAnim = ValueAnimator.ofFloat(0.2f, 1f)
        blinkAnim.duration = 800
        blinkAnim.repeatMode = ValueAnimator.REVERSE
        blinkAnim.repeatCount = ValueAnimator.INFINITE
        blinkAnim.addUpdateListener { tvTap.alpha = it.animatedValue as Float }
        blinkAnim.start()

        val langSuffix = when(currentLang) {
            "en" -> "en"
            "es" -> "es"
            "ja" -> "jp"
            else -> "br"
        }

        val sequences = arrayOf(
            intArrayOf(1, 2, 3, 1, 2, 3, 1),
            intArrayOf(4, 5, 6, 4, 5, 6, 4),
            intArrayOf(7, 8, 9, 7, 8, 9, 7),
            intArrayOf(10, 11, 12, 10, 11, 12, 10),
            intArrayOf(13, 14, 15, 13, 14, 15, 13),
            intArrayOf(16, 17, 18, 16, 17, 18, 16)
        )

        tutorialStep = 0
        tutorialFrameIndex = 0

        tutorialRunnable = Runnable {
            val currentSeq = sequences[tutorialStep]
            val frameNum = currentSeq[tutorialFrameIndex]

            val imgName = String.format("hamtutor%02d%s", frameNum, langSuffix)
            val resId = resources.getIdentifier(imgName, "drawable", packageName)

            if (resId != 0) {
                ivTut.setImageResource(resId)
            }

            tutorialFrameIndex = (tutorialFrameIndex + 1) % currentSeq.size
            tutorialHandler.postDelayed(tutorialRunnable, 500L)
        }

        tutorialHandler.post(tutorialRunnable)

        tutLayout.setOnClickListener {
            tutorialStep++
            tutorialFrameIndex = 0

            if (tutorialStep == 5) {
                val lp = tvTap.layoutParams as FrameLayout.LayoutParams
                lp.gravity = Gravity.CENTER
                tvTap.layoutParams = lp
            } else if (tutorialStep > 5) {
                tutorialHandler.removeCallbacks(tutorialRunnable)
                blinkAnim.cancel()

                prefs.edit().putBoolean("first_run", false).apply()

                tutLayout.animate().alpha(0f).setDuration(800).withEndAction {
                    tutLayout.visibility = View.GONE
                    showLayout(R.id.menuLayout)
                    playBGM(R.raw.inicial)
                    resetIdleTimer()
                }.start()
            }
        }
    }

    private fun showChampionScreen() {
        showLayout(R.id.championLayout)
        playBGM(R.raw.champion)

        val championLayout = findViewById<FrameLayout>(R.id.championLayout)
        val tvCredits = findViewById<TextView>(R.id.tvCredits)

        val creditsText = """
            DIRETORES E DESIGNERS
            Gabriel Formento
            
            PROGRAMADORES
            Gabriel Formento
            
            ARTISTAS (2D/3D/UI)
            Gabriel Formento
            
            DESIGNERS DE SOM E MÚSICA
            Gabriel Formento
            
            PRODUTOR
            Gabriel Formento
            
            QA (QUALITY ASSURANCE)
            Gabriel Formento
            
            LOCALIZAÇÃO
            Gabriel Formento
            
            MARKETING
            Gabriel Formento
            
            ESTÚDIO DE DESENVOLVIMENTO
            Actiomaster Studio
            
            PUBLISHER
            Actiomaster Studio
            
            AGRADECIMENTOS ESPECIAIS
            Pamela A. Gonçalves
            
            
            
            
            
            
        """.trimIndent()

        tvCredits.text = creditsText

        tvCredits.post {
            tvCredits.measure(
                View.MeasureSpec.makeMeasureSpec(championLayout.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            val realHeight = tvCredits.measuredHeight

            val lp = tvCredits.layoutParams
            lp.height = realHeight
            tvCredits.layoutParams = lp

            val screenHeight = championLayout.height.toFloat()
            val textHeight = realHeight.toFloat()

            tvCredits.translationY = screenHeight
            tvCredits.animate()
                .translationY(-textHeight - 400f)
                .setDuration(80000L)
                .start()
        }

        val endRun = Runnable {
            if (championLayout.visibility == View.VISIBLE) {
                championLayout.visibility = View.GONE
                tvCredits.animate().cancel()
                showLayout(R.id.menuLayout)
                playBGM(R.raw.inicial)
                resetIdleTimer()
            }
        }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(endRun, 85000)

        championLayout.setOnClickListener {
            handler.removeCallbacks(endRun)
            endRun.run()
        }
    }

    private fun showCustomConfirmDialog(titleText: String, messageText: String, onConfirm: () -> Unit, onCancel: (() -> Unit)? = null) {
        val bgDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#EE1E1E1E"))
            cornerRadius = 48f
            setStroke(3, Color.parseColor("#444444"))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 80, 80, 80)
            background = bgDrawable
        }

        val customTypefaceBold = Typeface.create("casual", Typeface.BOLD)
        val customTypefaceNormal = Typeface.create("casual", Typeface.NORMAL)

        val title = TextView(this).apply {
            text = titleText
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 28f
            typeface = customTypefaceBold
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
        }

        val desc = TextView(this).apply {
            text = messageText
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = customTypefaceNormal
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
        }

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val dialog = AlertDialog.Builder(this).setView(layout).setCancelable(false).create()

        val btnYes = AppCompatButton(this).apply {
            text = getStr("yes")
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4CAF50"))
                cornerRadius = 24f
            }
            isAllCaps = false
            typeface = customTypefaceBold
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }
            setOnClickListener {
                dialog.dismiss()
                onConfirm()
            }
        }

        val btnNo = AppCompatButton(this).apply {
            text = getStr("no")
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F44336"))
                cornerRadius = 24f
            }
            isAllCaps = false
            typeface = customTypefaceBold
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 10 }
            setOnClickListener {
                dialog.dismiss()
                onCancel?.invoke()
            }
        }

        btnLayout.addView(btnYes)
        btnLayout.addView(btnNo)

        layout.addView(title)
        layout.addView(desc)
        layout.addView(btnLayout)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun setupMenu() {
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnInfinite = findViewById<Button>(R.id.btnInfinite)
        val btnSurvival = findViewById<Button>(R.id.btnSurvival)

        btnStart.setOnClickListener { startGame(isAutoPlay = false, isInfinite = false, isSurvival = false) }

        /* === MODO AUTO-PLAY DESATIVADO PARA PRODUÇÃO ===
           (Para reativar nos testes, basta apagar este "/*" e o "*/" de baixo)
        btnStart.setOnLongClickListener {
            Toast.makeText(this, getStr("auto_act"), Toast.LENGTH_SHORT).show()
            startGame(isAutoPlay = true, isInfinite = false, isSurvival = false)
            true
        }
        */

        btnInfinite.setOnClickListener { startGame(isAutoPlay = false, isInfinite = true, isSurvival = false) }

        /* === MODO AUTO-PLAY DESATIVADO PARA PRODUÇÃO ===
        btnInfinite.setOnLongClickListener {
            Toast.makeText(this, getStr("auto_act"), Toast.LENGTH_SHORT).show()
            startGame(isAutoPlay = true, isInfinite = true, isSurvival = false)
            true
        }
        */

        btnSurvival.setOnClickListener { startGame(isAutoPlay = false, isInfinite = false, isSurvival = true) }

        /* === MODO AUTO-PLAY DESATIVADO PARA PRODUÇÃO ===
        btnSurvival.setOnLongClickListener {
            Toast.makeText(this, getStr("auto_act"), Toast.LENGTH_SHORT).show()
            startGame(isAutoPlay = true, isInfinite = false, isSurvival = true)
            true
        }
        */

        findViewById<Button>(R.id.btnRecordsMenu).setOnClickListener {
            showLayout(R.id.recordsLayoutRoot)
            updateRecordsList()
        }
        findViewById<RadioGroup>(R.id.rgRecMode).setOnCheckedChangeListener { _, checkedId ->
            currentRecMode = when (checkedId) {
                R.id.rbRecInf -> "inf"
                R.id.rbRecSurv -> "surv"
                else -> "norm"
            }
            updateRecordsList()
        }
        findViewById<Button>(R.id.btnBackFromRecords).setOnClickListener { showLayout(R.id.menuLayout) }

        findViewById<Button>(R.id.btnAchievementsMenu).setOnClickListener {
            showLayout(R.id.achievementsLayoutRoot)
            buildAchievementsGrid()
        }
        findViewById<Button>(R.id.btnBackFromAchievements).setOnClickListener { showLayout(R.id.menuLayout) }

        findViewById<Button>(R.id.btnStatsMenu).setOnClickListener {
            showLayout(R.id.statsLayoutRoot)
            updateStatsUI()
        }
        findViewById<RadioGroup>(R.id.rgStatsMode).setOnCheckedChangeListener { _, checkedId ->
            currentStatsMode = when (checkedId) {
                R.id.rbStatsInf -> "inf"
                R.id.rbStatsSurv -> "surv"
                else -> "norm"
            }
            updateStatsUI()
        }
        findViewById<Button>(R.id.btnBackFromStats).setOnClickListener { showLayout(R.id.menuLayout) }

        findViewById<Button>(R.id.btnOptionsMenu).setOnClickListener {
            showLayout(R.id.optionsLayoutRoot)
            updateSkinsRadios()
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener {
            showCustomConfirmDialog(
                titleText = getStr("exit_title"),
                messageText = getStr("exit_desc"),
                onConfirm = {
                    bgmPlayer?.stop()
                    bgmPlayer?.release()
                    sfxPlayer?.stop()
                    sfxPlayer?.release()
                    finishAffinity()
                    exitProcess(0)
                }
            )
        }

        val aboutLayout = findViewById<FrameLayout>(R.id.aboutLayout)
        findViewById<ImageButton>(R.id.btnAbout).setOnClickListener { aboutLayout.visibility = View.VISIBLE }
        aboutLayout.setOnClickListener { aboutLayout.visibility = View.GONE }

        checkMenuLayout()
    }

    private fun checkMenuLayout() {
        val hasFinishedLevel3 = prefs.getBoolean("ach_12", false)
        val btnInfinite = findViewById<Button>(R.id.btnInfinite)
        val btnSurvival = findViewById<Button>(R.id.btnSurvival)
        val layoutBonus = findViewById<LinearLayout>(R.id.layoutBonus)

        if (hasFinishedLevel3) {
            btnInfinite.visibility = View.VISIBLE
            btnSurvival.visibility = View.VISIBLE
            layoutBonus.visibility = View.VISIBLE
        } else {
            btnInfinite.visibility = View.GONE
            btnSurvival.visibility = View.GONE
            layoutBonus.visibility = View.GONE
        }
    }

    private fun startGame(isAutoPlay: Boolean, isInfinite: Boolean, isSurvival: Boolean) {
        val gameLayout = findViewById<View>(R.id.gameLayout)
        gameLayout.alpha = 1f
        showLayout(R.id.gameLayout)

        findViewById<View>(R.id.flashView).visibility = View.GONE
        findViewById<ImageView>(R.id.ivGameOver).visibility = View.GONE
        findViewById<LinearLayout>(R.id.bottomControlBar).visibility = View.VISIBLE
        findViewById<View>(R.id.gameDivisor).visibility = View.VISIBLE

        val useTouch = prefs.getBoolean("useTouch", true)
        val useWalls = prefs.getBoolean("useWalls", false)
        val useHQ = prefs.getBoolean("highQuality", true)
        val useBlood = prefs.getBoolean("bloodEnabled", false)
        val useFootprints = prefs.getBoolean("footprintsEnabled", true)
        val useDiagonal = prefs.getBoolean("diagonalEnabled", false)

        val sSnake = prefs.getInt("skinSnake", 0)
        val sField = prefs.getInt("skinField", 0)
        val sBonusFood = prefs.getInt("skinBonusFood", 0)

        val btnBack = findViewById<Button>(R.id.btnBackGame)
        val btnPause = findViewById<Button>(R.id.btnPauseGame)

        findViewById<LinearLayout>(R.id.dpadLayout).visibility = if (useTouch || isAutoPlay) View.GONE else View.VISIBLE

        if (isSurvival) {
            val survivalBGM = resources.getIdentifier("survival", "raw", packageName)
            if (survivalBGM != 0) playBGM(survivalBGM) else playBGM(R.raw.level3)
        } else {
            playBGM(R.raw.level1)
        }

        snakeGameView.setLangTexts(getStr("lvl"), getStr("pts"), getStr("auto"), "", getStr("inf"), getStr("surv"))
        snakeGameView.stopGame()
        snakeGameView.startGame(useTouch, useWalls, isAutoPlay, isInfinite, isSurvival, sSnake, sBonusFood, sField, useHQ, useBlood, useFootprints, useDiagonal)

        snakeGameView.post {
            snakeGameView.isPaused = true
        }

        isCountdownActive = true
        btnBack.isEnabled = false
        btnPause.isEnabled = false

        val tvCountdown = TextView(this).apply {
            text = "3"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 150f
            typeface = Typeface.create("casual", Typeface.BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(15f, 0f, 0f, Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val gameContainer = findViewById<FrameLayout>(R.id.gameContainer)
        gameContainer.addView(tvCountdown)

        var count = 3
        val cHandler = Handler(Looper.getMainLooper())

        val runnable = object : Runnable {
            override fun run() {
                count--
                if (count > 0) {
                    tvCountdown.text = count.toString()
                    cHandler.postDelayed(this, 1000)
                } else if (count == 0) {
                    val strGo = when (currentLang) {
                        "en" -> "GO!"
                        "es" -> "¡YA!"
                        "ja" -> "スタート!"
                        else -> "VAI!"
                    }
                    tvCountdown.text = strGo
                    cHandler.postDelayed(this, 1000)
                } else {
                    gameContainer.removeView(tvCountdown)

                    snakeGameView.setLangTexts(getStr("lvl"), getStr("pts"), getStr("auto"), getStr("paused"), getStr("inf"), getStr("surv"))

                    snakeGameView.isPaused = false
                    updateIngameTexts()

                    isCountdownActive = false
                    btnBack.isEnabled = true
                    btnPause.isEnabled = true
                }
            }
        }
        cHandler.postDelayed(runnable, 1000)

        if (!isAutoPlay) {
            val modePrefix = if (isInfinite) "_inf" else if (isSurvival) "_surv" else "_norm"
            val totalGames = prefs.getInt("stat${modePrefix}_games", 0)
            prefs.edit().putInt("stat${modePrefix}_games", totalGames + 1).apply()
        }
    }

    private fun setupOptionsAndSkins() {
        val rbTouch = findViewById<RadioButton>(R.id.rbTouch)
        val rbButtons = findViewById<RadioButton>(R.id.rbButtons)
        val swParedes = findViewById<Switch>(R.id.swParedes)
        val swHighQuality = findViewById<Switch>(R.id.swHighQuality)
        val swSound = findViewById<Switch>(R.id.swSound)
        val swBlood = findViewById<Switch>(R.id.swBlood)
        val swFootprints = findViewById<Switch>(R.id.swFootprints)
        val swDiagonal = findViewById<Switch>(R.id.swDiagonal)
        val sbMusicVolume = findViewById<SeekBar>(R.id.sbMusicVolume)

        if (prefs.getBoolean("useTouch", true)) rbTouch.isChecked = true else rbButtons.isChecked = true
        swParedes.isChecked = prefs.getBoolean("useWalls", false)
        swHighQuality.isChecked = prefs.getBoolean("highQuality", true)
        swSound.isChecked = isSoundEnabled
        swBlood.isChecked = isBloodEnabled
        swFootprints.isChecked = isFootprintsEnabled
        swDiagonal.isChecked = isDiagonalEnabled
        sbMusicVolume.progress = musicVolume

        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
        val thumbColors = intArrayOf(Color.parseColor("#4CAF50"), Color.parseColor("#F44336"))
        val trackColors = intArrayOf(Color.parseColor("#A5D6A7"), Color.parseColor("#EF9A9A"))
        val switchThumbList = ColorStateList(states, thumbColors)
        val switchTrackList = ColorStateList(states, trackColors)

        val radioColors = intArrayOf(Color.parseColor("#4CAF50"), Color.parseColor("#888888"))
        val radioColorList = ColorStateList(states, radioColors)
        val seekbarList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))

        val switches = listOf(swParedes, swHighQuality, swSound, swBlood, swFootprints, swDiagonal)
        switches.forEach {
            it.thumbTintList = switchThumbList
            it.trackTintList = switchTrackList
        }

        sbMusicVolume.progressTintList = seekbarList
        sbMusicVolume.thumbTintList = seekbarList

        val applyRadioColors = { rg: RadioGroup ->
            for (i in 0 until rg.childCount) {
                (rg.getChildAt(i) as? RadioButton)?.buttonTintList = radioColorList
            }
        }

        applyRadioColors(findViewById(R.id.rgControls))
        applyRadioColors(findViewById(R.id.rgSnakeSkin))
        applyRadioColors(findViewById(R.id.rgFieldSkin))
        applyRadioColors(findViewById(R.id.rgBonusSnakeSkin))
        applyRadioColors(findViewById(R.id.rgBonusFoodSkin))

        swSound.setOnCheckedChangeListener { _, isChecked -> toggleSound(isChecked) }

        sbMusicVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                musicVolume = progress
                val vol = progress / 100f
                bgmPlayer?.setVolume(vol, vol)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        findViewById<ImageButton>(R.id.btnLangPT).setOnClickListener { changeLanguage("pt") }
        findViewById<ImageButton>(R.id.btnLangEN).setOnClickListener { changeLanguage("en") }
        findViewById<ImageButton>(R.id.btnLangES).setOnClickListener { changeLanguage("es") }
        findViewById<ImageButton>(R.id.btnLangJA).setOnClickListener { changeLanguage("ja") }

        val etCheatCode = findViewById<EditText>(R.id.etCheatCode)
        val btnSubmitCheat = findViewById<Button>(R.id.btnSubmitCheat)

        btnSubmitCheat.setOnClickListener {
            val code = etCheatCode.text.toString().trim()
            if (code.equals("Actiomaster", ignoreCase = true)) {
                val editor = prefs.edit()
                for (i in 0..17) {
                    editor.putBoolean("ach_$i", true)
                }
                editor.apply()
                checkMenuLayout()
                updateSkinsRadios()
                Toast.makeText(this, getStr("cheat_activated"), Toast.LENGTH_LONG).show()
                etCheatCode.setText("")
            } else {
                Toast.makeText(this, getStr("cheat_invalid"), Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnBackFromOptions).setOnClickListener {
            val editor = prefs.edit()
                .putBoolean("useTouch", rbTouch.isChecked)
                .putBoolean("useWalls", swParedes.isChecked)
                .putBoolean("highQuality", swHighQuality.isChecked)
                .putBoolean("soundEnabled", swSound.isChecked)
                .putBoolean("bloodEnabled", swBlood.isChecked)
                .putBoolean("footprintsEnabled", swFootprints.isChecked)
                .putBoolean("diagonalEnabled", swDiagonal.isChecked)
                .putInt("musicVolume", sbMusicVolume.progress)

            isBloodEnabled = swBlood.isChecked
            isFootprintsEnabled = swFootprints.isChecked
            isDiagonalEnabled = swDiagonal.isChecked

            val rgSnake = findViewById<RadioGroup>(R.id.rgSnakeSkin)
            val rgBonusSnake = findViewById<RadioGroup>(R.id.rgBonusSnakeSkin)

            var snakeSkinId = 0
            val snakeIdx = rgSnake.indexOfChild(findViewById(rgSnake.checkedRadioButtonId))
            if (snakeIdx != -1) snakeSkinId = snakeIdx

            val bSnakeIdx = rgBonusSnake.indexOfChild(findViewById(rgBonusSnake.checkedRadioButtonId))
            if (bSnakeIdx != -1) snakeSkinId = bSnakeIdx + 5

            editor.putInt("skinSnake", snakeSkinId)

            val rgBonusFood = findViewById<RadioGroup>(R.id.rgBonusFoodSkin)
            val bFoodIdx = rgBonusFood.indexOfChild(findViewById(rgBonusFood.checkedRadioButtonId))
            editor.putInt("skinBonusFood", if(bFoodIdx != -1) bFoodIdx else 0)

            val rgField = findViewById<RadioGroup>(R.id.rgFieldSkin)
            val fieldIdx = rgField.indexOfChild(findViewById(rgField.checkedRadioButtonId))
            editor.putInt("skinField", if(fieldIdx != -1) fieldIdx else 0)

            editor.apply()
            showLayout(R.id.menuLayout)
            resetIdleTimer()
        }
    }

    private fun updateSkinsRadios() {
        val hasFominha = prefs.getBoolean("ach_1", false)
        val hasBanquete = prefs.getBoolean("ach_2", false)
        val hasRei = prefs.getBoolean("ach_3", false)
        val hasVeloz = prefs.getBoolean("ach_5", false)
        val hasOuro = prefs.getBoolean("ach_6", false)
        val hasCega = prefs.getBoolean("ach_7", false)
        val hasCliente = prefs.getBoolean("ach_8", false)
        val hasApetite = prefs.getBoolean("ach_9", false)

        val strB = getStr("locked")

        findViewById<RadioButton>(R.id.rbSnake1).apply { isEnabled = hasFominha; text = if(hasFominha) getStr("s_blue") else "${getStr("s_blue")} $strB" }
        findViewById<RadioButton>(R.id.rbSnake2).apply { isEnabled = hasBanquete; text = if(hasBanquete) getStr("s_red") else "${getStr("s_red")} $strB" }
        findViewById<RadioButton>(R.id.rbSnake3).apply { isEnabled = hasRei; text = if(hasRei) getStr("s_yellow") else "${getStr("s_yellow")} $strB" }
        findViewById<RadioButton>(R.id.rbSnake4).apply { isEnabled = hasVeloz; text = if(hasVeloz) getStr("s_black") else "${getStr("s_black")} $strB" }

        findViewById<RadioButton>(R.id.rbField1).apply { isEnabled = hasOuro; text = if(hasOuro) getStr("f_white") else "${getStr("f_white")} $strB" }
        findViewById<RadioButton>(R.id.rbField2).apply { isEnabled = hasCega; text = if(hasCega) getStr("f_gray") else "${getStr("f_gray")} $strB" }
        findViewById<RadioButton>(R.id.rbField3).apply { isEnabled = hasCliente; text = if(hasCliente) getStr("f_orange") else "${getStr("f_orange")} $strB" }
        findViewById<RadioButton>(R.id.rbField4).apply { isEnabled = hasApetite; text = if(hasApetite) getStr("f_cyan") else "${getStr("f_cyan")} $strB" }

        val savedSnake = prefs.getInt("skinSnake", 0)
        val rgSnake = findViewById<RadioGroup>(R.id.rgSnakeSkin)
        val rgBonusSnake = findViewById<RadioGroup>(R.id.rgBonusSnakeSkin)

        rgSnake.clearCheck()
        rgBonusSnake.clearCheck()

        if (savedSnake < 5) {
            val rb = rgSnake.getChildAt(savedSnake) as? RadioButton
            if (rb?.isEnabled == true) rb.isChecked = true else findViewById<RadioButton>(R.id.rbSnake0).isChecked = true
        } else {
            val rb = rgBonusSnake.getChildAt(savedSnake - 5) as? RadioButton
            rb?.isChecked = true
        }

        rgSnake.setOnCheckedChangeListener { _, id -> if (id != -1) rgBonusSnake.clearCheck() }
        rgBonusSnake.setOnCheckedChangeListener { _, id -> if (id != -1) rgSnake.clearCheck() }

        val savedField = prefs.getInt("skinField", 0)
        val rbField = findViewById<RadioGroup>(R.id.rgFieldSkin).getChildAt(savedField) as RadioButton
        if (rbField.isEnabled) rbField.isChecked = true else findViewById<RadioButton>(R.id.rbField0).isChecked = true

        val savedFood = prefs.getInt("skinBonusFood", 0)
        val rbFood = findViewById<RadioGroup>(R.id.rgBonusFoodSkin).getChildAt(savedFood) as? RadioButton
        rbFood?.isChecked = true
    }

    private fun changeLanguage(lang: String) {
        currentLang = lang
        prefs.edit().putString("language", lang).apply()
        applyLanguage()
        updateSkinsRadios()
        updateStatsUI()
    }

    private fun applyLanguage() {
        val titlesPT = arrayOf("A Primeira Mordida", "Fominha", "Banquete Sem Fim", "Rei do Buffet", "Maratona do Snack", "Veloz e Faminto", "Ouroboros", "Cobra Cega", "Cliente de Carteirinha", "Apetite Insaciável", "Aventureiro", "Destemido", "Audacioso", "Infinito 1", "Infinito 2", "Roedor de Sorte", "Encantador de Serpentes", "Roedor dos Roedores!")
        val descPT = arrayOf("Consuma o seu primeiro Hamster.", "Consuma 50 Hamsters numa partida.", "Consuma 75 Hamsters numa partida.", "Consuma 100 Hamsters numa partida.", "Sobreviva por 5 minutos numa partida.", "Consuma 10 Hamsters em 30 segundos.", "Morda a própria cauda.", "Bata na parede com menos de 10s.", "Inicie 100 partidas.", "Devore 10.000 Hamsters no total.", "Recebido ao concluir o Nível 1.", "Recebido ao concluir o Nível 2.", "Recebido ao concluir o Nível 3.", "Conquiste 2500 pontos no modo Infinito.", "Conquiste 5000 pontos no modo Infinito.", "Consuma 25 Cherry Fruits no modo Survival.", "Consuma 100 Cherry Fruits no modo Survival.", "Vença o modo Survival.")

        val titlesEN = arrayOf("First Bite", "Hungry", "Endless Feast", "Buffet King", "Snack Marathon", "Fast and Famished", "Ouroboros", "Blind Snake", "Regular Customer", "Insatiable Appetite", "Adventurer", "Fearless", "Audacious", "Infinite 1", "Infinite 2", "Lucky Rodent", "Snake Charmer", "Rodent of Rodents!")
        val descEN = arrayOf("Consume your first Hamster.", "Consume 50 Hamsters in one game.", "Consume 75 Hamsters in one game.", "Consume 100 Hamsters in one game.", "Survive for 5 minutes in one game.", "Consume 10 Hamsters in 30 seconds.", "Bite your own tail.", "Hit the wall in less than 10s.", "Start 100 games.", "Devour 10,000 Hamsters in total.", "Received upon completing Level 1.", "Received upon completing Level 2.", "Received upon completing Level 3.", "Score 2500 points in Infinite mode.", "Score 5000 points in Infinite mode.", "Consume 25 Cherry Fruits in Survival mode.", "Consume 100 Cherry Fruits in Survival mode.", "Win Survival mode.")

        val titlesES = arrayOf("Primer Bocado", "Hambriento", "Banquete Sin Fin", "Rey del Buffet", "Maratón de Snacks", "Rápido y Hambriento", "Uroboros", "Serpiente Ciega", "Cliente Habitual", "Apetito Insaciable", "Aventurero", "Intrépido", "Audaz", "Infinito 1", "Infinito 2", "Roedor Afortunado", "Encantador de Serpientes", "¡Roedor de Roedores!")
        val descES = arrayOf("Consume tu primer Hámster.", "Consume 50 Hámsters en una partida.", "Consume 75 Hámsters en una partida.", "Consume 100 Hámsters en una partida.", "Sobrevive por 5 minutos en una partida.", "Consume 10 Hámsters en 30 segundos.", "Muerde tu propia cola.", "Choca contra la pared en menos de 10s.", "Inicia 100 partidas.", "Devora 10,000 Hámsters en total.", "Recibido al completar el Nivel 1.", "Recibido al completar el Nivel 2.", "Recibido al completar el Nivel 3.", "Consigue 2500 puntos en modo Infinito.", "Consigue 5000 puntos en modo Infinito.", "Consume 25 Cherry Fruits en modo Survival.", "Consume 100 Cherry Fruits en modo Survival.", "Gana el modo Survival.")

        val titlesJA = arrayOf("最初の一口", "はらぺこ", "終わりのない宴", "ビュッフェの王様", "スナックマラソン", "早食い", "ウロボロス", "盲目のヘビ", "常連客", "飽くなき食欲", "冒険者", "恐れ知らず", "大胆不敵", "無限 1", "無限 2", "幸運なげっ歯類", "ヘビ使い", "げっ歯類中のげっ歯類！")
        val descJA = arrayOf("最初のハムスターを食べる。", "1回のゲームで50匹食べる。", "1回のゲームで75匹食べる。", "1回のゲームで100匹食べる。", "1回のゲームで5分間生き残る。", "30秒以内に10匹食べる。", "自分の尻尾を噛む。", "10秒未満で壁に衝突する。", "100回ゲームを開始する。", "合計10,000匹食べる。", "レベル1クリアで獲得。", "レベル2クリアで獲得。", "レベル3クリアで獲得。", "無限モードで2500点を獲得。", "無限モードで5000点を獲得。", "サバイバルモードでチェリーを25個食べる。", "サバイバルモードでチェリーを100個食べる。", "サバイバルモードをクリアする。")

        achTitles = when(currentLang) { "en" -> titlesEN; "es" -> titlesES; "ja" -> titlesJA; else -> titlesPT }
        achDesc = when(currentLang) { "en" -> descEN; "es" -> descES; "ja" -> descJA; else -> descPT }

        findViewById<Button>(R.id.btnStart).text = getStr("start")
        findViewById<Button>(R.id.btnInfinite).text = getStr("infinite")
        findViewById<Button>(R.id.btnSurvival).text = getStr("surv")
        findViewById<Button>(R.id.btnRecordsMenu).text = getStr("records")
        findViewById<Button>(R.id.btnAchievementsMenu).text = getStr("achievements")
        findViewById<Button>(R.id.btnStatsMenu).text = getStr("stats")
        findViewById<Button>(R.id.btnOptionsMenu).text = getStr("options")
        findViewById<Button>(R.id.btnExit).text = getStr("exit")

        findViewById<Button>(R.id.btnBackFromRecords).text = getStr("back")
        findViewById<Button>(R.id.btnBackFromAchievements).text = getStr("back")
        findViewById<Button>(R.id.btnBackFromStats).text = getStr("back")
        findViewById<Button>(R.id.btnBackFromOptions).text = getStr("save")
        findViewById<Button>(R.id.btnBackGame).text = getStr("back")
        updateIngameTexts()

        findViewById<TextView>(R.id.tvLangSel).text = getStr("lang_sel")
        findViewById<TextView>(R.id.tvOptTitle).text = getStr("options")
        findViewById<TextView>(R.id.tvStatsTitle).text = getStr("stats")
        findViewById<TextView>(R.id.tvAchTitle).text = getStr("achievements")
        findViewById<TextView>(R.id.tvRecTitle).text = getStr("top10")

        findViewById<TextView>(R.id.tvOptControl).text = getStr("control")
        findViewById<RadioButton>(R.id.rbTouch).text = getStr("touch")
        findViewById<RadioButton>(R.id.rbButtons).text = getStr("buttons")

        findViewById<Switch>(R.id.swParedes).text = getStr("walls")
        findViewById<Switch>(R.id.swHighQuality).text = getStr("hq")
        findViewById<Switch>(R.id.swSound).text = getStr("sound")
        findViewById<Switch>(R.id.swBlood).text = getStr("blood")
        findViewById<Switch>(R.id.swFootprints).text = getStr("foot")
        findViewById<Switch>(R.id.swDiagonal).text = getStr("diag")
        findViewById<TextView>(R.id.tvOptVolume).text = getStr("vol")

        findViewById<TextView>(R.id.tvOptSnake).text = getStr("skin_s")
        findViewById<TextView>(R.id.tvOptField).text = getStr("skin_f")
        findViewById<TextView>(R.id.tvOptBonus).text = getStr("bonus_unlocked")
        findViewById<TextView>(R.id.tvOptBonusSnake).text = getStr("bonus_snake")
        findViewById<TextView>(R.id.tvOptBonusFood).text = getStr("bonus_food")

        findViewById<RadioButton>(R.id.rbSnake0).text = getStr("s_def")
        findViewById<RadioButton>(R.id.rbField0).text = getStr("f_def")

        findViewById<RadioButton>(R.id.rbFood0).text = getStr("food_0")
        findViewById<RadioButton>(R.id.rbFood1).text = getStr("food_1")
        findViewById<RadioButton>(R.id.rbFood2).text = getStr("food_2")
        findViewById<RadioButton>(R.id.rbFood3).text = getStr("food_3")
        findViewById<RadioButton>(R.id.rbFood4).text = getStr("food_4")
        findViewById<RadioButton>(R.id.rbFood5).text = getStr("food_5")
        findViewById<RadioButton>(R.id.rbFood6).text = getStr("food_6")

        findViewById<RadioButton>(R.id.rbSnake5).text = getStr("b_s_1")
        findViewById<RadioButton>(R.id.rbSnake6).text = getStr("b_s_2")
        findViewById<RadioButton>(R.id.rbSnake7).text = getStr("b_s_3")
        findViewById<RadioButton>(R.id.rbSnake8).text = getStr("b_s_4")
        findViewById<RadioButton>(R.id.rbSnake9).text = getStr("b_s_5")
        findViewById<RadioButton>(R.id.rbSnake10).text = getStr("b_s_6")

        findViewById<RadioButton>(R.id.rbSnake11).text = getStr("b_s_7")
        findViewById<RadioButton>(R.id.rbSnake12).text = getStr("b_s_8")
        findViewById<RadioButton>(R.id.rbSnake13).text = getStr("b_s_9")

        findViewById<RadioButton>(R.id.rbField0).text = getStr("f_def")
        findViewById<RadioButton>(R.id.rbField1).text = getStr("f_white")
        findViewById<RadioButton>(R.id.rbField2).text = getStr("f_gray")
        findViewById<RadioButton>(R.id.rbField3).text = getStr("f_orange")
        findViewById<RadioButton>(R.id.rbField4).text = getStr("f_cyan")

        findViewById<TextView>(R.id.tvSecretCode).text = getStr("secret_code")
        findViewById<EditText>(R.id.etCheatCode).hint = getStr("type_code")

        findViewById<RadioButton>(R.id.rbStatsNorm).text = getStr("norm")
        findViewById<RadioButton>(R.id.rbStatsInf).text = getStr("infinite")
        findViewById<RadioButton>(R.id.rbStatsSurv).text = getStr("surv")
        findViewById<RadioButton>(R.id.rbRecNorm).text = getStr("norm")
        findViewById<RadioButton>(R.id.rbRecInf).text = getStr("infinite")
        findViewById<RadioButton>(R.id.rbRecSurv).text = getStr("surv")
    }

    private fun updateIngameTexts() {
        val btnPause = findViewById<Button>(R.id.btnPauseGame)
        btnPause.text = if (snakeGameView.isPaused) getStr("play") else getStr("pause")
    }

    private fun unlockAchievement(index: Int) {
        val key = "ach_$index"
        if (!prefs.getBoolean(key, false)) {
            prefs.edit().putBoolean(key, true).apply()

            runOnUiThread {
                val layout = findViewById<LinearLayout>(R.id.achPopupAlert)
                val ivIcon = findViewById<ImageView>(R.id.ivAchPopupIcon)
                val tvTitle = findViewById<TextView>(R.id.tvAchPopupTitle)

                val numFormatado = String.format("%02d", index + 1)
                val iconResId = resources.getIdentifier("c$numFormatado", "drawable", packageName)

                if (iconResId != 0) {
                    ivIcon.setImageResource(iconResId)
                } else {
                    ivIcon.setBackgroundColor(Color.parseColor("#FFD700"))
                }
                tvTitle.text = achTitles[index]

                val sfxBadge = resources.getIdentifier("badgeunlock", "raw", packageName)
                if (sfxBadge != 0) playSFX(sfxBadge)

                layout.animate().translationY(0f).setDuration(500).withEndAction {
                    Handler(Looper.getMainLooper()).postDelayed({
                        layout.animate().translationY(-800f).setDuration(500).start()
                    }, 3500)
                }.start()
            }
        }
    }

    private fun checkAchievements(hamstersThisGame: Int, timeAliveMs: Long, hitSelf: Boolean, hitWall: Boolean, score: Int, isInf: Boolean, isSurv: Boolean, isWin: Boolean) {
        val editor = prefs.edit()

        val modePfx = if (isInf) "_inf" else if (isSurv) "_surv" else "_norm"

        val totalH = prefs.getInt("stat${modePfx}_total_hamsters", 0) + hamstersThisGame
        val totalTime = prefs.getLong("stat${modePfx}_total_time", 0L) + timeAliveMs
        val maxH = maxOf(prefs.getInt("stat${modePfx}_max_hamsters", 0), hamstersThisGame)
        val maxT = maxOf(prefs.getLong("stat${modePfx}_max_time", 0L), timeAliveMs)

        editor.putInt("stat${modePfx}_total_hamsters", totalH)
        editor.putLong("stat${modePfx}_total_time", totalTime)
        editor.putInt("stat${modePfx}_max_hamsters", maxH)
        editor.putLong("stat${modePfx}_max_time", maxT)
        editor.apply()

        if (!isInf && !isSurv) {
            if (totalH > 0) unlockAchievement(0)
            if (hamstersThisGame >= 50) unlockAchievement(1)
            if (hamstersThisGame >= 75) unlockAchievement(2)
            if (hamstersThisGame >= 100) unlockAchievement(3)
            if (timeAliveMs >= 300000) unlockAchievement(4)
            if (snakeGameView.achievedFastEat) unlockAchievement(5)
            if (hitSelf) unlockAchievement(6)
            if (hitWall && timeAliveMs < 10000 && timeAliveMs > 0) unlockAchievement(7)
            if (prefs.getInt("stat_norm_games", 0) >= 100) unlockAchievement(8)
            if (totalH >= 10000) unlockAchievement(9)
        }

        if (isInf) {
            if (score >= 2500) unlockAchievement(13)
            if (score >= 5000) unlockAchievement(14)
        }

        if (isSurv) {
            if (hamstersThisGame >= 25) unlockAchievement(15)
            if (hamstersThisGame >= 100) unlockAchievement(16)
            if (isWin) unlockAchievement(17)
        }
    }

    private fun buildAchievementsGrid() {
        val grid = findViewById<GridLayout>(R.id.achievementsGrid)
        grid.removeAllViews()

        val scale = resources.displayMetrics.density
        val tamanhoMedalha = (90 * scale + 0.5f).toInt()
        val margem = (8 * scale + 0.5f).toInt()

        for (i in 0 until 18) {
            val isUnlocked = prefs.getBoolean("ach_$i", false)
            val numFormatado = String.format("%02d", i + 1)

            val iconName = if (isUnlocked) "c$numFormatado" else "b$numFormatado"
            val iconResId = resources.getIdentifier(iconName, "drawable", packageName)

            val imageView = ImageView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = tamanhoMedalha
                    height = tamanhoMedalha
                    setMargins(margem, margem, margem, margem)
                }

                if (iconResId != 0) setImageResource(iconResId) else setBackgroundColor(Color.DKGRAY)

                setOnClickListener {
                    showAchievementPopup(i, isUnlocked, iconResId)
                }
            }
            grid.addView(imageView)
        }
    }

    private fun showAchievementPopup(index: Int, isUnlocked: Boolean, iconResId: Int) {
        val bgDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#1E1E1E"))
            cornerRadius = 48f
            setStroke(3, Color.parseColor("#444444"))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 80, 80, 80)
            background = bgDrawable
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(250, 250).apply { bottomMargin = 40 }
            if (iconResId != 0) setImageResource(iconResId) else setBackgroundColor(Color.DKGRAY)
        }

        val customTypefaceBold = Typeface.create("casual", Typeface.BOLD)
        val title = TextView(this).apply {
            text = achTitles[index]
            setTextColor(if (isUnlocked) Color.parseColor("#FFD700") else Color.WHITE)
            textSize = 24f
            typeface = customTypefaceBold
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 20 }
        }

        val desc = TextView(this).apply {
            val dtxt = if (isUnlocked) achDesc[index] else "${getStr("locked")}\n\n${achDesc[index]}"
            text = dtxt
            setTextColor(Color.parseColor("#BBBBBB"))
            textSize = 16f
            typeface = Typeface.create("casual", Typeface.NORMAL)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 50 }
        }

        val dialog = AlertDialog.Builder(this).setView(layout).create()

        val btnClose = AppCompatButton(this).apply {
            text = getStr("back")
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { setColor(Color.parseColor("#333333")); cornerRadius = 24f }
            isAllCaps = false
            typeface = customTypefaceBold
            setPadding(50, 20, 50, 20)
            setOnClickListener { dialog.dismiss() }
        }

        layout.addView(icon)
        layout.addView(title)
        layout.addView(desc)
        layout.addView(btnClose)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun updateStatsUI() {
        val prefix = "_$currentStatsMode"
        val games = prefs.getInt("stat${prefix}_games", 0)
        val totalSecs = prefs.getLong("stat${prefix}_total_time", 0L) / 1000
        val totalH = prefs.getInt("stat${prefix}_total_hamsters", 0)
        val maxH = prefs.getInt("stat${prefix}_max_hamsters", 0)
        val maxSecs = prefs.getLong("stat${prefix}_max_time", 0L) / 1000
        val avgSecs = if (games > 0) totalSecs / games else 0

        val strGames = if (currentLang == "en") "Games" else if (currentLang == "es") "Partidas" else if (currentLang == "ja") "プレイ回数" else "Partidas"

        var sHams = ""
        var sMax = ""
        if (currentStatsMode == "surv") {
            sHams = if (currentLang == "en") "Cherries Collected" else if (currentLang == "es") "Cherries Recolectadas" else if (currentLang == "ja") "集めたチェリー" else "Cherries Coletadas"
            sMax = if (currentLang == "en") "Max Cherries" else if (currentLang == "es") "Máximo de Cherries" else if (currentLang == "ja") "最大チェリー" else "Máximo de Cherries"
        } else {
            sHams = if (currentLang == "en") "Snacks Eaten" else if (currentLang == "es") "Snacks Devorados" else if (currentLang == "ja") "食べたスナック" else "Snacks Devorados"
            sMax = if (currentLang == "en") "Max Size" else if (currentLang == "es") "Tamaño Máximo" else if (currentLang == "ja") "最大サイズ" else "Maior Tamanho"
        }

        val sTMax = if (currentLang == "en") "Max Time" else if (currentLang == "es") "Tiempo Máximo" else if (currentLang == "ja") "最大時間" else "Tempo Máximo"
        val sTAvg = if (currentLang == "en") "Avg Time" else if (currentLang == "es") "Tiempo Medio" else if (currentLang == "ja") "平均時間" else "Tempo Médio"

        val info = """
            🕹 $strGames: $games
            🍒 $sHams: $totalH
            🏆 $sMax: $maxH
            ⏱ $sTMax: ${maxSecs}s
            ⚖ $sTAvg: ${avgSecs}s
        """.trimIndent()
        findViewById<TextView>(R.id.tvStatsInfo).text = info
    }

    private fun setupGameControls() {
        val btnPause = findViewById<Button>(R.id.btnPauseGame)

        findViewById<Button>(R.id.btnBackGame).setOnClickListener {
            if (isCountdownActive) return@setOnClickListener

            snakeGameView.isPaused = true
            updateIngameTexts()

            showCustomConfirmDialog(
                titleText = getStr("back_title"),
                messageText = getStr("back_desc"),
                onConfirm = {
                    snakeGameView.stopGame()
                    showLayout(R.id.menuLayout)
                    playBGM(R.raw.inicial)
                    resetIdleTimer()
                },
                onCancel = {
                    snakeGameView.isPaused = false
                    updateIngameTexts()
                }
            )
        }

        btnPause.setOnClickListener {
            if (isCountdownActive) return@setOnClickListener

            snakeGameView.isPaused = !snakeGameView.isPaused
            updateIngameTexts()
            if (snakeGameView.isPaused) bgmPlayer?.pause() else bgmPlayer?.start()
        }

        findViewById<Button>(R.id.btnUp).setOnClickListener { snakeGameView.setDirection(SnakeGameView.Direction.UP) }
        findViewById<Button>(R.id.btnDown).setOnClickListener { snakeGameView.setDirection(SnakeGameView.Direction.DOWN) }
        findViewById<Button>(R.id.btnLeft).setOnClickListener { snakeGameView.setDirection(SnakeGameView.Direction.LEFT) }
        findViewById<Button>(R.id.btnRight).setOnClickListener { snakeGameView.setDirection(SnakeGameView.Direction.RIGHT) }

        snakeGameView.onLevelUpCallback = { level ->
            when (level) {
                2 -> {
                    playBGM(R.raw.level2)
                    unlockAchievement(10)
                }
                3 -> {
                    playBGM(R.raw.level3)
                    unlockAchievement(11)
                }
                4 -> {
                    unlockAchievement(12)
                    checkMenuLayout()
                    runOnUiThread { showChampionScreen() }
                }
            }
        }

        snakeGameView.onEatCallback = { playSFX(R.raw.eating) }

        snakeGameView.onGameOverCallback = { score, hamsters, time, hitSelf, hitWall, isAutoPlay, isWin ->
            runOnUiThread {
                val isInf = snakeGameView.isInfiniteMode
                val isSurv = snakeGameView.isSurvivalMode

                if (!isAutoPlay) {
                    checkAchievements(hamsters, time, hitSelf, hitWall, score, isInf, isSurv, isWin)
                }

                if (isSurv && isWin) {
                    val sfxWin = resources.getIdentifier("survivalwin", "raw", packageName)
                    if (sfxWin != 0) playBGM(sfxWin, isLooping = false) else playBGM(R.raw.gameover, isLooping = false)
                } else {
                    playBGM(R.raw.gameover, isLooping = false)
                }

                if (isSoundEnabled) vibratePhone()

                val flashView = findViewById<View>(R.id.flashView)
                val ivGameOver = findViewById<ImageView>(R.id.ivGameOver)

                if (isSurv && isWin) {
                    val imgWin = resources.getIdentifier("survivalwin", "drawable", packageName)
                    if (imgWin != 0) ivGameOver.setImageResource(imgWin) else ivGameOver.setImageResource(R.drawable.gameover)
                } else {
                    ivGameOver.setImageResource(R.drawable.gameover)
                }

                flashView.visibility = View.VISIBLE
                flashView.alpha = 1f
                flashView.animate().alpha(0f).setDuration(600).withEndAction {
                    flashView.visibility = View.GONE

                    ivGameOver.visibility = View.VISIBLE
                    ivGameOver.alpha = 0f
                    ivGameOver.animate().alpha(1f).setDuration(1200).withEndAction {

                        Handler(Looper.getMainLooper()).postDelayed({
                            checkHighScore(score, isWin, if (isInf) "inf" else if (isSurv) "surv" else "norm")
                        }, 1000)
                    }
                }
            }
        }
    }

    private fun checkHighScore(score: Int, isWin: Boolean, mode: String) {
        val bgDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#EE1E1E1E"))
            cornerRadius = 48f
            setStroke(3, Color.parseColor("#444444"))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(80, 80, 80, 80)
            background = bgDrawable
        }

        val customTypefaceBold = Typeface.create("casual", Typeface.BOLD)
        val customTypefaceNormal = Typeface.create("casual", Typeface.NORMAL)

        val title = TextView(this).apply {
            text = if (isWin) getStr("victory") else getStr("gameover")
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 28f
            typeface = customTypefaceBold
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 10 }
        }

        val desc = TextView(this).apply {
            text = "${getStr("score_str")}: $score\n\n${getStr("type_name")}"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = customTypefaceNormal
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 30 }
        }

        val input = EditText(this).apply {
            filters = arrayOf(InputFilter.LengthFilter(11))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            hint = "..."
            textSize = 20f
            typeface = customTypefaceBold
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                cornerRadius = 16f
            }
            setPadding(30, 20, 30, 20)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
            isSingleLine = true
        }

        val dialog = AlertDialog.Builder(this).setView(layout).setCancelable(false).create()

        val btnSave = AppCompatButton(this).apply {
            text = getStr("save")
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4CAF50"))
                cornerRadius = 24f
            }
            isAllCaps = false
            typeface = customTypefaceBold
            setPadding(50, 20, 50, 20)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

            setOnClickListener {
                val name = input.text.toString().ifBlank { getStr("anon") }
                saveRecord(name, score, mode)

                this@MainActivity.findViewById<ImageView>(R.id.ivGameOver).visibility = View.GONE
                snakeGameView.stopGame()

                showLayout(R.id.menuLayout)
                playBGM(R.raw.inicial)
                resetIdleTimer()
                dialog.dismiss()
            }
        }

        layout.addView(title)
        layout.addView(desc)
        layout.addView(input)
        layout.addView(btnSave)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun saveRecord(name: String, score: Int, mode: String) {
        val key = "records_$mode"
        val recordsString = prefs.getString(key, "") ?: ""
        val records = recordsString.split(";").filter { it.isNotEmpty() }.toMutableList()
        records.add("$name,$score")
        val top10 = records.sortedByDescending { it.split(",")[1].toInt() }.take(10)
        prefs.edit().putString(key, top10.joinToString(";")).apply()
    }

    private fun updateRecordsList() {
        val key = "records_$currentRecMode"
        val tvRecords = findViewById<TextView>(R.id.tvRecordsList)
        val recordsString = prefs.getString(key, "") ?: ""
        if (recordsString.isEmpty()) {
            tvRecords.text = getStr("no_rec")
            return
        }
        val formattedText = StringBuilder()
        recordsString.split(";").forEachIndexed { index, record ->
            val data = record.split(",")
            formattedText.append("${index + 1}. ${data[0].padEnd(11, ' ')} - ${data[1]}\n")
        }
        tvRecords.text = formattedText.toString()
    }

    private fun showLayout(layoutId: Int) {
        findViewById<View>(R.id.menuLayout).visibility = View.GONE
        findViewById<View>(R.id.optionsLayoutRoot).visibility = View.GONE
        findViewById<View>(R.id.recordsLayoutRoot).visibility = View.GONE
        findViewById<View>(R.id.achievementsLayoutRoot).visibility = View.GONE
        findViewById<View>(R.id.statsLayoutRoot).visibility = View.GONE
        findViewById<View>(R.id.gameLayout).visibility = View.GONE
        findViewById<View>(R.id.championLayout).visibility = View.GONE

        val bgMenu = findViewById<ImageView>(R.id.bgMenu)
        if (layoutId == R.id.gameLayout || layoutId == R.id.championLayout) {
            bgMenu.visibility = View.GONE
        } else {
            bgMenu.visibility = View.VISIBLE
        }

        findViewById<View>(layoutId).visibility = View.VISIBLE
    }

    private fun getStr(key: String): String {
        val dictPT = mapOf(
            "lang_sel" to "Seleção de Idioma:", "start" to "INICIAR", "infinite" to "Infinito", "surv" to "Survival", "records" to "Recordes", "achievements" to "Conquistas",
            "stats" to "Estatísticas", "options" to "Opções", "exit" to "Sair", "back" to "Voltar", "save" to "Salvar",
            "control" to "Controles:", "touch" to "Touch", "buttons" to "Botões", "walls" to "Paredes Mortais ", "hq" to "Gráficos HQ ", "sound" to "Sons ",
            "blood" to "Efeito Sangue ", "foot" to "Pegadas ", "diag" to "Diagonal ", "vol" to "Volume:",
            "skin_s" to "Skin da Cobra:", "skin_f" to "Estilo do Campo:", "bonus_snake" to "Skins Bônus (Snake):", "bonus_food" to "Skins Bônus (Snack):",
            "locked" to "(Bloqueado)", "bonus_unlocked" to "BÔNUS DESBLOQUEADOS!", "norm" to "Padrão",
            "s_def" to "Jararaca", "s_blue" to "Cobra Real", "s_red" to "Naja", "s_yellow" to "Jiboia", "s_black" to "Mamba Negra",
            "f_def" to "Grama", "f_white" to "Areia", "f_gray" to "Pedras", "f_orange" to "Barro", "f_cyan" to "Neve",
            "food_0" to "Hamster (Padrão)", "food_1" to "Porquinho da Índia", "food_2" to "Gerbil", "food_3" to "Twister", "food_4" to "Chinchila", "food_5" to "Camundongo", "food_6" to "Rato-Pigmeu",
            "b_s_1" to "Coral", "b_s_2" to "Cascavel", "b_s_3" to "Tigre", "b_s_4" to "Bungarus", "b_s_5" to "Taipan", "b_s_6" to "Xenodermus",
            "b_s_7" to "Sucuri", "b_s_8" to "Surucucu", "b_s_9" to "Titanoboa",
            "auto_act" to "MODO AUTO-PLAY ATIVADO!", "top10" to "TOP 10", "no_rec" to "Nenhum recorde.", "gameover" to "FIM DE JOGO!", "victory" to "VITÓRIA!",
            "score_str" to "Pontuação", "type_name" to "Digite seu nome:", "anon" to "Anônimo", "lvl" to "Lvl", "pts" to "Pts",
            "auto" to "AUTO", "paused" to "PAUSADO", "play" to "Play", "pause" to "Pause", "inf" to "Infinito", "tap_to_return" to "Toque na tela para voltar...",
            "secret_code" to "CÓDIGO SECRETO:", "type_code" to "Digite o código...", "cheat_activated" to "Cheat Ativado! Tudo desbloqueado!", "cheat_invalid" to "Código Inválido!",
            "tap_to_advance" to "Toque na tela para avançar", "yes" to "Sim", "no" to "Não", "exit_title" to "Sair do Jogo", "exit_desc" to "Deseja realmente sair?", "back_title" to "Voltar ao Menu", "back_desc" to "Deseja encerrar a partida?"
        )

        val dictEN = mapOf(
            "lang_sel" to "Language Selection:", "start" to "START", "infinite" to "Infinite", "surv" to "Survival", "records" to "Records", "achievements" to "Achievements",
            "stats" to "Stats", "options" to "Options", "exit" to "Exit", "back" to "Back", "save" to "Save",
            "control" to "Controls:", "touch" to "Touch", "buttons" to "Buttons", "walls" to "Deadly Walls ", "hq" to "HQ Graphics ", "sound" to "Sounds ",
            "blood" to "Blood FX ", "foot" to "Footprints ", "diag" to "Diagonal ", "vol" to "Volume:",
            "skin_s" to "Snake Skin:", "skin_f" to "Field Skin:", "bonus_snake" to "Bonus Skins (Snake):", "bonus_food" to "Bonus Skins (Snack):",
            "locked" to "(Locked)", "bonus_unlocked" to "BONUS UNLOCKED!", "norm" to "Standard",
            "s_def" to "Jararaca", "s_blue" to "King Cobra", "s_red" to "Cobra", "s_yellow" to "Boa", "s_black" to "Black Mamba",
            "f_def" to "Grass", "f_white" to "Sand", "f_gray" to "Stones", "f_orange" to "Mud", "f_cyan" to "Snow",
            "food_0" to "Hamster (Default)", "food_1" to "Guinea Pig", "food_2" to "Gerbil", "food_3" to "Twister", "food_4" to "Chinchilla", "food_5" to "Mouse", "food_6" to "Pygmy Mouse",
            "b_s_1" to "Coral", "b_s_2" to "Rattlesnake", "b_s_3" to "Tiger Snake", "b_s_4" to "Bungarus", "b_s_5" to "Taipan", "b_s_6" to "Xenodermus",
            "b_s_7" to "Anaconda", "b_s_8" to "Bushmaster", "b_s_9" to "Titanoboa",
            "auto_act" to "AUTO-PLAY ACTIVATED!", "top10" to "TOP 10", "no_rec" to "No records.", "gameover" to "GAME OVER!", "victory" to "VICTORY!",
            "score_str" to "Score", "type_name" to "Enter your name:", "anon" to "Anonymous", "lvl" to "Lvl", "pts" to "Pts",
            "auto" to "AUTO", "paused" to "PAUSED", "play" to "Play", "pause" to "Pause", "inf" to "Infinite", "tap_to_return" to "Tap the screen to return...",
            "secret_code" to "SECRET CODE:", "type_code" to "Type code...", "cheat_activated" to "Cheat Activated! All unlocked!", "cheat_invalid" to "Invalid Code!",
            "tap_to_advance" to "Tap the screen to advance", "yes" to "Yes", "no" to "No", "exit_title" to "Exit Game", "exit_desc" to "Do you really want to exit?", "back_title" to "Back to Menu", "back_desc" to "Do you really want to end the game?"
        )

        val dictES = mapOf(
            "lang_sel" to "Selección de Idioma:", "start" to "INICIAR", "infinite" to "Infinito", "surv" to "Survival", "records" to "Récords", "achievements" to "Logros",
            "stats" to "Estadísticas", "options" to "Opciones", "exit" to "Salir", "back" to "Volver", "save" to "Guardar",
            "control" to "Controles:", "touch" to "Táctil", "buttons" to "Botones", "walls" to "Muros Mortales ", "hq" to "Gráficos HQ ", "sound" to "Sonidos ",
            "blood" to "Efecto Sangre ", "foot" to "Huellas ", "diag" to "Diagonal ", "vol" to "Volumen:",
            "skin_s" to "Serpiente:", "skin_f" to "Campo:", "bonus_snake" to "Skins Bonus (Serpiente):", "bonus_food" to "Skins Bonus (Snack):",
            "locked" to "(Bloqueado)", "bonus_unlocked" to "¡BONUS DESBLOQUEADO!", "norm" to "Estándar",
            "s_def" to "Jararaca", "s_blue" to "Cobra Real", "s_red" to "Naja", "s_yellow" to "Boa", "s_black" to "Mamba Negra",
            "f_def" to "Césped", "f_white" to "Arena", "f_gray" to "Piedras", "f_orange" to "Barro", "f_cyan" to "Nieve",
            "food_0" to "Hámster (Defecto)", "food_1" to "Conejillo de Indias", "food_2" to "Jerbo", "food_3" to "Twister", "food_4" to "Chinchilla", "food_5" to "Ratón", "food_6" to "Ratón Pigmeo",
            "b_s_1" to "Coral", "b_s_2" to "Cascabel", "b_s_3" to "Tigre", "b_s_4" to "Bungarus", "b_s_5" to "Taipan", "b_s_6" to "Xenodermus",
            "b_s_7" to "Anaconda", "b_s_8" to "Cuaima", "b_s_9" to "Titanoboa",
            "auto_act" to "¡AUTO-PLAY ACTIVADO!", "top10" to "TOP 10", "no_rec" to "Sin récords.", "gameover" to "¡FIN DEL JUEGO!", "victory" to "¡VICTORIA!",
            "score_str" to "Puntuación", "type_name" to "Tu nombre:", "anon" to "Anónimo", "lvl" to "Nivel", "pts" to "Pts",
            "auto" to "AUTO", "paused" to "PAUSA", "play" to "Play", "pause" to "Pausa", "inf" to "Infinito", "tap_to_return" to "Toca la pantalla para volver...",
            "secret_code" to "CÓDIGO SECRETO:", "type_code" to "Escribe el código...", "cheat_activated" to "¡Truco Activado! ¡Todo desbloqueado!", "cheat_invalid" to "¡Código Inválido!",
            "tap_to_advance" to "Toca la pantalla para avanzar", "yes" to "Sí", "no" to "No", "exit_title" to "Salir del Juego", "exit_desc" to "¿Realmente deseas salir?", "back_title" to "Volver al Menú", "back_desc" to "¿Realmente deseas terminar la partida?"
        )

        val dictJA = mapOf(
            "lang_sel" to "言語選択:", "start" to "スタート", "infinite" to "無限", "surv" to "サバイバル", "records" to "記録", "achievements" to "実績",
            "stats" to "統計", "options" to "設定", "exit" to "終了", "back" to "戻る", "save" to "保存",
            "control" to "操作:", "タッチ" to "タッチ", "buttons" to "ボタン", "walls" to "壁の衝突 ", "hq" to "高画質 ", "sound" to "音 ",
            "blood" to "流血効果 ", "foot" to "足跡 ", "diag" to "斜め移動 ", "vol" to "音量:",
            "skin_s" to "ヘビのスキン:", "skin_f" to "フィールド:", "bonus_snake" to "ボーナススキン (ヘビ):", "bonus_food" to "ボーナススキン (スナック):",
            "locked" to "(ロック)", "bonus_unlocked" to "ボーナス解放！", "norm" to "標準",
            "s_def" to "ハララカ", "s_blue" to "キングコブラ", "s_red" to "コブラ", "s_yellow" to "ボア", "s_black" to "ブラックマンバ",
            "f_def" to "草", "f_white" to "砂", "f_gray" to "石", "f_orange" to "泥", "f_cyan" to "雪",
            "food_0" to "ハムスター (標準)", "food_1" to "モルモット", "food_2" to "ジャービル", "food_3" to "ツイスター", "food_4" to "チンチラ", "food_5" to "マウス", "food_6" to "ピグミーマウス",
            "b_s_1" to "サンゴヘビ", "b_s_2" to "ガラガラヘビ", "b_s_3" to "トラヘビ", "b_s_4" to "アマガサヘビ", "b_s_5" to "タイパン", "b_s_6" to "ゼノデルムス",
            "b_s_7" to "アナコンダ", "b_s_8" to "ブッシュマスター", "b_s_9" to "ティタノボア",
            "auto_act" to "自動プレイ開始！", "top10" to "トップ 10", "no_rec" to "記録なし", "gameover" to "ゲームオーバー！", "victory" to "勝利！",
            "score_str" to "スコア", "type_name" to "名前を入力:", "anon" to "匿名", "lvl" to "Lv", "pts" to "点",
            "auto" to "自動", "paused" to "一時停止", "play" to "再生", "pause" to "停止", "inf" to "無限", "tap_to_return" to "画面をタッチして戻る...",
            "secret_code" to "シークレットコード:", "type_code" to "コードを入力...", "cheat_activated" to "チート有効！全解放！", "cheat_invalid" to "無効なコード！",
            "tap_to_advance" to "画面をタッチして進む", "yes" to "はい", "no" to "いいえ", "exit_title" to "ゲーム終了", "exit_desc" to "本当に終了しますか？", "back_title" to "メニューに戻る", "back_desc" to "本当にゲームを終了しますか？"
        )

        return when (currentLang) {
            "en" -> dictEN[key] ?: key
            "es" -> dictES[key] ?: key
            "ja" -> dictJA[key] ?: key
            else -> dictPT[key] ?: key
        }
    }
}