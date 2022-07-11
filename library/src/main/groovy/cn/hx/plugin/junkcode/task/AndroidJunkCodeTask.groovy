package cn.hx.plugin.junkcode.task

import cn.hx.plugin.junkcode.ext.JunkCodeConfig
import cn.hx.plugin.junkcode.template.ManifestTemplate
import cn.hx.plugin.junkcode.template.ResTemplate
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import groovy.text.GStringTemplateEngine
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.TaskAction

import javax.lang.model.element.Modifier

class AndroidJunkCodeTask extends DefaultTask {

    static def random = new Random()

    static abc = "abcdefghijklmnopqrstuvwxyz".toCharArray()

    @Nested
    JunkCodeConfig config = new JunkCodeConfig()

    @Input
    String manifestPackageName = ""

    @OutputDirectories
    File outDir

    @TaskAction
    void generateJunkCode() {
        if (outDir.exists()) {
            outDir.deleteDir()
        }
        //通过成类
        generateClasses()
        //生成资源
        generateRes()
    }

    /**
     * 生成java代码和AndroidManifest.xml
     */
    void generateClasses() {
        def javaDir = new File(outDir, "java")
        for (int i = 0; i < config.packageCount; i++) {
            String packageName = config.packageBase + "." + generateName(i)
            //生成Activity
            for (int j = 0; j < config.activityCountPerPackage; j++) {
                def activityPreName = generateName(j)
                generateActivity(packageName, activityPreName)
            }
            //生成其它类
            for (int j = 0; j < config.otherCountPerPackage; j++) {
                def className = generateName(j).capitalize()
                def typeBuilder = TypeSpec.classBuilder(className)
                typeBuilder.addModifiers(Modifier.PUBLIC)
                for (int k = 0; k < config.methodCountPerClass; k++) {
                    def methodName = generateName(k)
                    def methodBuilder = MethodSpec.methodBuilder(methodName)
                    generateMethods(methodBuilder)
                    typeBuilder.addMethod(methodBuilder.build())
                }
                def fileBuilder = JavaFile.builder(packageName, typeBuilder.build())
                fileBuilder.build().writeTo(javaDir)
            }
        }
    }

    /**
     * 生成随机方法
     * @param methodBuilder
     */
    static void generateMethods(MethodSpec.Builder methodBuilder) {
        switch (random.nextInt(7)) {
            case 1:
                def time = System.currentTimeMillis()
                def timeSay = "Current Time:" + time
                methodBuilder.addStatement("long now = \$T.currentTimeMillis()", System.class)
                        .beginControlFlow("if (\$T.currentTimeMillis() < now)", System.class)
                        .addStatement("\$T.out.println(\$S)", System.class, timeSay)
                        .nextControlFlow("else if (\$T.currentTimeMillis() == now)", System.class)
                        .addStatement("\$T.out.println(\$S)", System.class, "Time stood still!")
                        .nextControlFlow("else")
                        .addStatement("\$T.out.println(\$S)", System.class, "Ok, time still moving forward")
                        .endControlFlow()
                break
            case 2:
                methodBuilder.beginControlFlow("try")
                        .addStatement("throw new Exception(\$S)", "Failed")
                        .nextControlFlow("catch (\$T e)", Exception.class)
                        .addStatement("throw new \$T(e)", RuntimeException.class)
                        .endControlFlow()
                break
            case 3:
                def value = Math.random().toString()
                methodBuilder.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(void.class)
                        .addParameter(String[].class, "args")
                        .addStatement("\$T.out.println(\$S)", System.class, value)
                break
            default:
                def value = Math.random().toString()
                methodBuilder.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(void.class)
                        .addParameter(String[].class, "args")
                        .addStatement("\$T.out.println(\$S)", System.class, value)
        }
    }

    /**
     * 生成Activity
     * @param packageName
     * @param activityPreName
     */
    void generateActivity(String packageName, String activityPreName) {
        def javaDir = new File(outDir, "java")
        def className = activityPreName.capitalize() + "Activity"
        def layoutName = "${config.resPrefix.toLowerCase()}${packageName.replace(".", "_")}_activity_${activityPreName}"
        generateLayout(layoutName)
        if (!config.excludeActivityJavaFile) {
            def typeBuilder = TypeSpec.classBuilder(className)
            typeBuilder.superclass(ClassName.get("android.app", "Activity"))
            typeBuilder.addModifiers(Modifier.PUBLIC)
            //onCreate方法
            def bundleClassName = ClassName.get("android.os", "Bundle")
            typeBuilder.addMethod(MethodSpec.methodBuilder("onCreate")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PROTECTED)
                    .addParameter(bundleClassName, "savedInstanceState")
                    .addStatement("super.onCreate(savedInstanceState)")
                    .addStatement("setContentView(\$T.layout.${layoutName})", ClassName.get(manifestPackageName, "R"))
                    .build())
            //其它方法
            for (int j = 0; j < config.methodCountPerClass; j++) {
                def methodName = generateName(j)
                def methodBuilder = MethodSpec.methodBuilder(methodName)
                generateMethods(methodBuilder)
                typeBuilder.addMethod(methodBuilder.build())
            }
            def fileBuilder = JavaFile.builder(packageName, typeBuilder.build())
            fileBuilder.build().writeTo(javaDir)
        }
        addToManifestByFileIo(className, packageName)
    }

    /**
     * 生成资源文件
     */
    void generateRes() {
        //生成drawable
        for (int i = 0; i < config.drawableCount; i++) {
            def drawableName = "${config.resPrefix.toLowerCase()}${generateName(i)}"
            generateDrawableNew(drawableName)
        }
        //生成string
        for (int i = 0; i < config.stringCount; i++) {
            def name = "${config.resPrefix.toLowerCase()}${generateName(i)}"
            def value = name
            addStringByFileIo(name, value)
        }
    }

    /**
     * 生成layout
     * @param layoutName
     */
    void generateDrawable(String drawableName) {
        def drawableFile = new File(outDir, "res/drawable/${drawableName}.xml")
        if (!drawableFile.getParentFile().exists()) {
            drawableFile.getParentFile().mkdirs()
        }
        if (!drawableFile.exists()) {
            drawableFile.createNewFile()
        }
        FileWriter writer
        try {
            writer = new FileWriter(drawableFile)
            def binding = [
                    offset1 : random.nextInt(1000000),
                    offset2 : random.nextInt(1000000),
            ]
            def template = makeTemplate(ResTemplate.DRAWABLE, binding)
            writer.write(template.toString())
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            if (writer != null) {
                writer.close()
            }
        }
    }


    static def randomDrawable = new Random()
    void generateDrawableNew(String drawableName) {
        generateLayoutWithDrawableName(drawableName)
        def drawableFile = new File(outDir, "res/drawable/${drawableName}.xml")
        if (!drawableFile.getParentFile().exists()) {
            drawableFile.getParentFile().mkdirs()
        }
        if (!drawableFile.exists()) {
            drawableFile.createNewFile()
        }
        FileWriter writer
        try {
            writer = new FileWriter(drawableFile)
            def binding = [
                    offset1 : randomDrawable.nextInt(100),
                    offset2 : randomDrawable.nextInt(100),
                    offset3 : randomDrawable.nextInt(100),
                    offset4 : randomDrawable.nextInt(100),
                    offset5 : randomDrawable.nextInt(100),
                    offset6 : randomDrawable.nextInt(100)
            ]
            def template = makeTemplate(ResTemplate.DRAWABLE2, binding)
            writer.write(template.toString())
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            if (writer != null) {
                writer.close()
            }
        }
    }


    /**
     * 生成layout
     * @param layoutName
     */
    void generateLayout(String layoutName) {
        def layoutFile = new File(outDir, "res/layout/${layoutName}.xml")
        if (!layoutFile.getParentFile().exists()) {
            layoutFile.getParentFile().mkdirs()
        }
        if (!layoutFile.exists()) {
            layoutFile.createNewFile()
        }
        FileWriter writer
        try {
            writer = new FileWriter(layoutFile)
            def template = ResTemplate.LAYOUT_TEMPLATE
            writer.write(template.toString())
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            if (writer != null) {
                writer.close()
            }
        }
    }
    
    void generateLayoutWithDrawableName(String Name) {
        def layoutName = "${config.resPrefix.toLowerCase()}_activity_${Name}"
        def layoutFile = new File(outDir, "res/layout/${layoutName}.xml")
        if (!layoutFile.getParentFile().exists()) {
            layoutFile.getParentFile().mkdirs()
        }
        if (!layoutFile.exists()) {
            layoutFile.createNewFile()
        }
        FileWriter writer
        try {
            writer = new FileWriter(layoutFile)
            def binding = [
                    offset1 : randomDrawable.nextDouble().round(2),
                    offset2 : randomDrawable.nextDouble().round(2),
                    offset3 : layoutName,
            ]
            def template = makeTemplate(ResTemplate.LAYOUT_TEMPLATE2, binding)
            writer.write(template.toString())
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            if (writer != null) {
                writer.close()
            }
        }
    }


    /**
     * 通过文件读写流的方式将新创建的Activity加入清单文件
     *
     * @param activityName
     * @param packageName
     */
    void addToManifestByFileIo(String activityName, String packageName) {
        def manifestFile = new File(outDir, "AndroidManifest.xml")
        if (!manifestFile.getParentFile().exists()) {
            manifestFile.getParentFile().mkdirs()
        }
        if (!manifestFile.exists()) {
            def template = ManifestTemplate.TEMPLATE
            FileWriter writer
            try {
                writer = new FileWriter(manifestFile)
                writer.write(template.toString())
            } catch (Exception e) {
                e.printStackTrace()
            } finally {
                if (writer != null) {
                    writer.close()
                }
            }
        }
        FileReader reader
        FileWriter writer
        try {
            reader = new FileReader(manifestFile)
            StringBuilder sb = new StringBuilder()
            // 每一行的内容
            String line = ""
            while ((line = reader.readLine()) != null) {
                // 找到application节点的末尾
                if (line.contains("</application>")) {
                    // 在application节点最后插入新创建的activity节点
                    def binding = [
                            packageName : packageName,
                            activityName: activityName,
                    ]
                    def template = makeTemplate(ManifestTemplate.ACTIVITY_NODE, binding)
                    sb.append(template.toString() + "\n")
                }
                sb.append(line + "\n")
            }
            String content = sb.toString()
            writer = new FileWriter(manifestFile)
            writer.write(content)
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            if (reader != null) {
                reader.close()
            }
            if (writer != null) {
                writer.close()
            }
        }
    }

    /**
     * 将string写入strings.xml
     * @param name
     * @param value
     */
    void addStringByFileIo(String name, String value) {
        //生成string
        def stringFile = new File(outDir, "res/values/strings.xml")
        if (!stringFile.getParentFile().exists()) {
            stringFile.getParentFile().mkdirs()
        }
        if (!stringFile.exists()) {
            stringFile.createNewFile()
            FileWriter writer
            try {
                writer = new FileWriter(stringFile)
                def template = ResTemplate.TEMPLATE
                writer.write(template.toString())
            } catch (Exception e) {
                e.printStackTrace()
            } finally {
                if (writer != null) {
                    writer.close()
                }
            }
        }
        FileReader reader
        FileWriter writer
        try {
            reader = new FileReader(stringFile)
            StringBuilder sb = new StringBuilder()
            // 每一行的内容
            String line = ""
            while ((line = reader.readLine()) != null) {
                // 找到resources节点的末尾
                if (line.contains("</resources>")) {
                    // 在resources节点最后插入新创建的string节点
                    def binding = [
                            stringName : name,
                            stringValue: value,
                    ]
                    def template = makeTemplate(ResTemplate.STRING_NODE, binding)
                    sb.append(template.toString() + "\n")
                }
                sb.append(line + "\n")
            }
            String content = sb.toString()
            writer = new FileWriter(stringFile)
            writer.write(content)
        } catch (Exception e) {
            e.printStackTrace()
        } finally {
            if (reader != null) {
                reader.close()
            }
            if (writer != null) {
                writer.close()
            }
        }
    }

    /**
     * 加载模板
     *
     * @param template
     * @param binding
     * @return
     */
    static def makeTemplate(def template, def binding) {
        def engine = new GStringTemplateEngine()
        return engine.createTemplate(template).make(binding)
    }

    /**
     * 生成名称
     * @param index
     * @return
     */
    static String generateName(int index) {
        def sb = new StringBuffer()
        for (i in 0..4) {
            sb.append(abc[random.nextInt(abc.size())])
        }
        int temp = index
        while (temp >= 0) {
            sb.append(abc[temp % abc.size()])
            temp = temp / abc.size()
            if (temp == 0) {
                temp = -1
            }
        }
        sb.append(index.toString())
        return sb.toString()
    }
}