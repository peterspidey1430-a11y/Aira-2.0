package com.aira.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.aira.agent.ai.ActionExecutor
import com.aira.agent.ai.GeminiEngine
import com.aira.agent.ai.TaskPlanner
import com.aira.agent.background.BackgroundAssistantService
import com.aira.agent.data.AiraRepository
import com.aira.agent.device.DeviceController
import com.aira.agent.errors.AiraError
import com.aira.agent.errors.ErrorHandler
import com.aira.agent.learning.PreferenceLearner
import com.aira.agent.memory.MemoryManager
import com.aira.agent.mood.MoodAnalyzer
import com.aira.agent.multitasking.TaskExecutionManager
import com.aira.agent.messaging.MessagingController
import com.aira.agent.messaging.CallingController
import com.aira.agent.media.SpotifyController
import com.aira.agent.media.YouTubeController
import com.aira.agent.permissions.PermissionManager
import com.aira.agent.personality.AiraPersonality
import com.aira.agent.security.SecureStorage
import com.aira.agent.web.BrowserController
import com.aira.agent.web.SearchController
import com.aira.agent.voice.VoiceAssistant

/**
 * Application root. Owns the long-lived singletons (the "agent")
 * and wires them together. Modules access dependencies via [AiraApp].
 *
 * All long-lived components are kept here so they survive
 * configuration changes and remain reachable from background services.
 */
class AiraApplication : Application() {

    lateinit var repository: AiraRepository
        private set

    lateinit var secureStorage: SecureStorage
        private set

    lateinit var personality: AiraPersonality
        private set

    lateinit var gemini: GeminiEngine
        private set

    lateinit var memory: MemoryManager
        private set

    lateinit var learner: PreferenceLearner
        private set

    lateinit var mood: MoodAnalyzer
        private set

    lateinit var permissions: PermissionManager
        private set

    lateinit var device: DeviceController
        private set

    lateinit var messaging: MessagingController
        private set

    lateinit var calling: CallingController
        private set

    lateinit var browser: BrowserController
        private set

    lateinit var search: SearchController
        private set

    lateinit var youTube: YouTubeController
        private set

    lateinit var spotify: SpotifyController
        private set

    lateinit var taskPlanner: TaskPlanner
        private set

    lateinit var executor: ActionExecutor
        private set

    lateinit var multitask: TaskExecutionManager
        private set

    lateinit var voice: VoiceAssistant
        private set

    lateinit var errors: ErrorHandler
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        errors = ErrorHandler()

        secureStorage = SecureStorage(this)
        repository = AiraRepository(this)
        personality = AiraPersonality(this)
        memory = MemoryManager(this, repository)
        learner = PreferenceLearner(this, memory)
        mood = MoodAnalyzer()
        permissions = PermissionManager(this)

        gemini = GeminiEngine(secureStorage) { errors }

        device = DeviceController(this)
        messaging = MessagingController(this)
        calling = CallingController(this)
        browser = BrowserController(this)
        search = SearchController(this)
        youTube = YouTubeController(this)
        spotify = SpotifyController(this)

        executor = ActionExecutor(
            device = device,
            messaging = messaging,
            calling = calling,
            browser = browser,
            search = search,
            youTube = youTube,
            spotify = spotify,
            permissions = permissions,
            memory = memory,
            errors = errors
        )

        taskPlanner = TaskPlanner(gemini, executor, memory, mood, learner, errors)
        multitask = TaskExecutionManager(taskPlanner)

        voice = VoiceAssistant(
            context = this,
            executor = executor,
            memory = memory,
            mood = mood,
            personality = personality,
            taskPlanner = taskPlanner,
            multitask = multitask,
            errors = errors
        )

        createNotificationChannels()
        Log.i(TAG, "Aira initialised. Model=${BuildConfig.GEMINI_MODEL_PRIMARY}")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CH_VOICE,
                "Voice assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows when Aira is listening." }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CH_TASKS,
                "Active tasks",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows current task progress." }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CH_PROACTIVE,
                "Background assistant",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Quiet proactive suggestions from Aira."
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val TAG = "AiraApp"
        const val CH_VOICE = "aira_voice"
        const val CH_TASKS = "aira_tasks"
        const val CH_PROACTIVE = "aira_proactive"

        @Volatile
        private var instance: AiraApplication? = null

        fun get(): AiraApplication = instance
            ?: throw AiraError.Fatal("AiraApplication not initialised")
    }
}