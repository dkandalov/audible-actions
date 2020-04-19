package fridaymario;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import fridaymario.listeners.*;
import fridaymario.sounds.SilentSound;
import fridaymario.sounds.Sounds;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtilRt.isEmptyOrSpaces;

public class IntelliJAppComponent implements AppLifecycleListener {
	public static IntelliJAppComponent instance;
	private ActionListeningSoundPlayer soundPlayer;
	private Disposable disposable;
	private boolean silentMode;
	private boolean logUnmappedActions;

	public IntelliJAppComponent() {
		instance = this;
	}

	@Override public void appFrameCreated(@NotNull List<String> commandLineArgs) {
		if (!Settings.getInstance().isPluginEnabled()) return;
		disposable = init();
	}

	@Override public void appWillBeClosed(boolean isRestart) {
		if (!Settings.getInstance().isPluginEnabled()) return;
		if (soundPlayer != null && Settings.getInstance().backgroundMusicEnabled) {
			soundPlayer.stopAndPlayGameOver();
		}
	}

	private Disposable init() {
		Disposable disposable = Disposer.newDisposable("FridayMario");
		Disposer.register(ApplicationManager.getApplication(), disposable);

		soundPlayer = new ActionListeningSoundPlayer(createSounds(), createLoggingListener()).init();
		Disposer.register(disposable, () -> {
			if (soundPlayer != null) {
				soundPlayer.stop();
				soundPlayer = null;
			}
		});
		initApplicationListeners(soundPlayer, disposable);
		initProjectListeners(soundPlayer, disposable);

		// see https://github.com/dkandalov/friday-mario/issues/3#issuecomment-160421286
		// and http://keithp.com/blogs/Java-Sound-on-Linux/
		String clipProperty = System.getProperty("javax.sound.sampled.Clip");
		if (SystemInfo.isLinux && clipProperty != null && clipProperty.equals("org.classpath.icedtea.pulseaudio.PulseAudioMixerProvider")) {
			show("JDK used by your IDE can lock up or fail to play sounds.<br/>" +
				 "Please see <a href=\"http://keithp.com/blogs/Java-Sound-on-Linux/\">http://keithp.com/blogs/Java-Sound-on-Linux</a> to fix it.");
		}
		return disposable;
	}

	private void initApplicationListeners(ActionListeningSoundPlayer soundPlayer, Disposable disposable) {
		new AllActions(soundPlayer).start(disposable);
	}

	private void initProjectListeners(ActionListeningSoundPlayer soundPlayer, Disposable disposable) {
		ProjectManagerListener projectManagerListener = new ProjectManagerListener() {
			@Override public void projectOpened(@NotNull Project project) {
				// TODO create child of project AND disposable?
				new Refactoring(project, soundPlayer).start(project);
				new VcsActions(project, soundPlayer).start(project);
				Compilation.factory.create(project, soundPlayer).start(project);
				new UnitTests(project, soundPlayer).start(project);
			}
		};

		// TODO is this necessary?
		for (Project project : ProjectManager.getInstance().getOpenProjects()) {
			projectManagerListener.projectOpened(project);
		}

		ProjectManager.getInstance().addProjectManagerListener(projectManagerListener, disposable);
	}

	public IntelliJAppComponent silentMode() {
		silentMode = true;
		return this;
	}

	public IntelliJAppComponent logUnmappedActionsMode() {
		logUnmappedActions = true;
		return this;
	}

	private Sounds createSounds() {
		if (silentMode) {
			return Sounds.createSilent(new SilentSound.Listener() {
				@Override public void playing(String soundName) {
					show(soundName);
				}

				@Override public void stopped(String soundName) {
					show("stopped: " + soundName);
				}
			});
		} else {
			Settings settings = Settings.getInstance();
			return Sounds.create(settings.actionSoundsEnabled, settings.backgroundMusicEnabled);
		}
	}

	private ActionListeningSoundPlayer.Listener createLoggingListener() {
		return new ActionListeningSoundPlayer.Listener() {
			@Override public void unmappedAction(String actionId) {
				if (logUnmappedActions) show(actionId);
			}

			@Override public void unmappedRefactoring(String refactoringId) {
				if (logUnmappedActions) show(refactoringId);
			}
		};
	}

	private static void show(String message) {
		if (isEmptyOrSpaces(message)) return;
		String noTitle = "";
		Notification notification = new Notification("Friday Mario", noTitle, message, NotificationType.INFORMATION);
		ApplicationManager.getApplication().getMessageBus().syncPublisher(Notifications.TOPIC).notify(notification);
	}

	public void setBackgroundMusicEnabled(boolean value) {
		Settings.getInstance().setBackgroundMusicEnabled(value);
		update();
	}

	public void setActionSoundsEnabled(boolean value) {
		Settings.getInstance().setActionSoundsEnabled(value);
		update();
	}

	private void update() {
		if (disposable != null) disposable.dispose();
		if (Settings.getInstance().isPluginEnabled()) disposable = init();
	}
}