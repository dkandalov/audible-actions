package fridaymario.listeners;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class InitJavaCompilation {
	public InitJavaCompilation() {
		MessageBusConnection connect = ApplicationManager.getApplication().getMessageBus().connect();
		connect.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
			@Override public void appFrameCreated(@NotNull List<String> commandLineArgs) {
				Compilation.factory = (project, listener) -> {
					CompilationStatusListener compilationListener = new CompilationStatusListener() {
						@Override public void compilationFinished(boolean aborted,
						                                          int errors,
						                                          int warnings,
						                                          @NotNull CompileContext compileContext) {
							if (errors > 0) {
								listener.compilationFailed();
							} else {
								listener.compilationSucceeded();
							}
						}
					};
					return new Compilation() {
						@Override public void start(Disposable disposable) {
							CompilerManager.getInstance(project).addCompilationStatusListener(compilationListener, disposable);
						}
					};
				};
			}
		});
	}
}
