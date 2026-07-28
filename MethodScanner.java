import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class MethodScanner {
    public static void main(String[] args) throws Exception {
        File classesDir = new File("d:/Capstone/source/mkaix.healthsyncbe/target/classes");
        URL[] urls = { classesDir.toURI().toURL() };
        URLClassLoader classLoader = new URLClassLoader(urls, MethodScanner.class.getClassLoader());

        String[] packagesToScan = {"com.g93.be.controller", "com.g93.be.service.impl"};
        List<String> results = new ArrayList<>();
        int counter = 1;
        
        System.out.println("No\tModule Name\tMethod Name\tSheet Name\tDescription\tPre-Condition");

        for (String pkg : packagesToScan) {
            File pkgDir = new File(classesDir, pkg.replace('.', '/'));
            if (!pkgDir.exists()) continue;

            for (File file : pkgDir.listFiles()) {
                if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                    String className = pkg + "." + file.getName().replace(".class", "");
                    try {
                        Class<?> clazz = classLoader.loadClass(className);
                        // Skip interfaces and abstract classes
                        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                            continue;
                        }

                        Method[] methods = clazz.getDeclaredMethods();
                        for (Method method : methods) {
                            int mod = method.getModifiers();
                            // Only include public methods, not static, not synthetic
                            if (Modifier.isPublic(mod) && !Modifier.isStatic(mod) && !method.isSynthetic()) {
                                String simpleClassName = clazz.getSimpleName();
                                String methodName = method.getName();
                                
                                // Skip getters, setters, and basic object methods
                                if (methodName.startsWith("get") || methodName.startsWith("set") || 
                                    methodName.equals("toString") || methodName.equals("hashCode") || 
                                    methodName.equals("equals") || methodName.equals("canEqual")) {
                                    continue;
                                }

                                String sheetName = methodName;
                                if (pkg.contains("controller")) {
                                    sheetName = simpleClassName + "." + methodName;
                                    // Excel sheet names are max 31 chars
                                    if (sheetName.length() > 31) {
                                        sheetName = sheetName.substring(0, 31);
                                    }
                                }

                                System.out.println(String.format("%d\t%s\t%s\t%s\t\t", 
                                    counter++, simpleClassName, methodName, sheetName));
                            }
                        }
                    } catch (Throwable t) {
                        // ignore class loading errors for specific classes
                    }
                }
            }
        }
    }
}
