package us.msu.cse.repair.core.faultlocalizer;

import com.gzoltar.core.agent.AgentCreator;
import com.gzoltar.core.agent.RegistrySingleton;
import com.gzoltar.core.components.Component;
import com.gzoltar.core.components.Statement;
import com.gzoltar.core.diag.SFL;
import com.gzoltar.core.exec.parameters.ClassParameters;
import com.gzoltar.core.exec.parameters.TestParameters;
import com.gzoltar.core.instr.message.IMessage;
import com.gzoltar.core.instr.message.Message;
import com.gzoltar.core.instr.message.Response;
import com.gzoltar.core.instr.testing.TestResult;
import com.gzoltar.core.spectra.Spectra;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GZoltarLauncher {
    private static boolean debug = false;
    private static final String pathSeparator = System.getProperty("path.separator");
    private static final String fileSeparator = System.getProperty("file.separator");
    private static final String runner = "com.gzoltar.core.instr.Runner";
    private static final String javaHome;

    private File agent;
    private String workingDirectory;
    private ClassParameters classParameters;
    private TestParameters testParameters;
    private ArrayList<String> classpaths;
    private Spectra spectra;

    public GZoltarLauncher(String workingDirectory) throws IOException {
        this.workingDirectory = workingDirectory;
        this.classParameters = new ClassParameters();
        this.testParameters = new TestParameters();
        this.classpaths = new ArrayList<>();
        this.agent = AgentCreator.extract(
                "com/gzoltar/core/components",
                "com/gzoltar/core/instr",
                "com/gzoltar/core/exec",
                "junit",
                "org/junit",
                "org/hamcrest",
                "org/objectweb/asm",
                "com/google/common"
        );
    }

    public void run() {
        List<TestResult> testResults = compute();
        this.spectra = new Spectra();
        this.spectra.registerResults(testResults);
        SFL.sfl(this.spectra);
    }

    public List<TestResult> getTestResults() {
        return this.spectra.getTestResults();
    }

    public void addClassToInstrument(String name) {
        this.classParameters.addClassToInstrument(name);
    }

    public void addPackageToInstrument(String name) {
        this.classParameters.addPackageToInstrument(name);
    }

    public void addClassNotToInstrument(String name) {
        this.classParameters.addClassNotToInstrument(name);
    }

    public void addPackageNotToInstrument(String name) {
        this.classParameters.addPackageNotToInstrument(name);
    }

    public void addTestToExecute(String name) {
        this.testParameters.addTestToExecute(name);
    }

    public void addTestPackageToExecute(String name) {
        this.testParameters.addTestPackageToExecute(name);
    }

    public void addTestNotToExecute(String name) {
        this.testParameters.addTestNotToExecute(name);
    }

    public void addTestPackageNotToExecute(String name) {
        this.testParameters.addTestPackageNotToExecute(name);
    }

    public ClassParameters getClassParameters() {
        return this.classParameters;
    }

    public void setClassParameters(ClassParameters classParameters) {
        this.classParameters = classParameters;
    }

    public TestParameters getTestParameters() {
        return this.testParameters;
    }

    public void setTestParameters(TestParameters testParameters) {
        this.testParameters = testParameters;
    }

    public String getWorkingDirectory() {
        return this.workingDirectory;
    }

    public void setWorkingDirectory(String wD) {
        this.workingDirectory = wD;
    }

    public ArrayList<String> getClasspaths() {
        return this.classpaths;
    }

    public void setClassPaths(ArrayList<String> cPs) {
        this.classpaths.addAll(cPs);
    }

    public Spectra getSpectra() {
        return this.spectra;
    }

    public List<Component> getSuspiciousComponents() {
        return this.spectra.getComponents();
    }

    public List<Statement> getSuspiciousStatements() {
        List<Component> allComponents = this.spectra.getComponents();
        List<Statement> statements = new ArrayList<>();

        for (Component component : allComponents) {
            if (component instanceof Statement) {
                statements.add((Statement) component);
            }
        }

        return statements;
    }

    private List<TestResult> compute() {
        RegistrySingleton.createSingleton();
        Response response = null;

        try {
            IMessage message = new Message();
            message.setClassParameters(getClassParameters());
            message.setTestParameters(getTestParameters());
            String messageName = UUID.randomUUID().toString();
            RegistrySingleton.register(messageName, message);
            StringBuilder classpath = new StringBuilder(System.getProperty("java.class.path") + pathSeparator + getWorkingDirectory());

            for (String cp : getClasspaths()) {
                classpath.append(pathSeparator).append(cp);
            }

            List<String> cmds = new ArrayList<>();
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                cmds.add(javaHome + ".exe");
            } else {
                cmds.add(javaHome);
            }

            cmds.add("-javaagent:" + agent.getAbsolutePath());
            cmds.add("-cp");
            cmds.add(classpath.toString());
            cmds.add("com.gzoltar.core.instr.Runner");
            cmds.add(Integer.toString(RegistrySingleton.getPort()));
            cmds.add(messageName);
            ProcessBuilder pb = new ProcessBuilder(cmds);
            Map<String, String> environment = pb.environment();
            environment.put("FAULT_LOCALIZATION", "true");
            pb.directory(new File(getWorkingDirectory()));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            InputStream is = p.getInputStream();
            BufferedInputStream isl = new BufferedInputStream(is);
            byte[] buffer = new byte[1024];

            if (debug) {
                System.err.println(">>> Begin subprocess output");
            }

            int len;
            while((len = isl.read(buffer)) != -1) {
                if (debug) {
                    System.err.write(buffer, 0, len);
                }
            }

            if (debug) {
                System.err.println("<<< End subprocess output");
            }

            p.waitFor();
            response = message.getResponse();
        } catch (Exception var13) {
            var13.printStackTrace();
        }

        RegistrySingleton.unregister();
        return response != null ? response.getTestResults() : new ArrayList<TestResult>();
    }

    static {
        javaHome = System.getProperty("java.home") + fileSeparator + "bin" + fileSeparator + "java";
    }
}
