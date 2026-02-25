package gg.fotia.chat.api;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Addon类加载器
 */
public class AddonClassLoader extends URLClassLoader {

    private final File file;
    private final AddonInfo addonInfo;

    public AddonClassLoader(File file, AddonInfo addonInfo, ClassLoader parent) throws Exception {
        super(new URL[]{file.toURI().toURL()}, parent);
        this.file = file;
        this.addonInfo = addonInfo;
    }

    /**
     * 获取Addon文件
     */
    public File getFile() {
        return file;
    }

    /**
     * 获取Addon信息
     */
    public AddonInfo getAddonInfo() {
        return addonInfo;
    }

    /**
     * 加载Addon主类
     */
    public FotiaChatAddon loadAddon() throws Exception {
        Class<?> mainClass = Class.forName(addonInfo.getMain(), true, this);

        if (!FotiaChatAddon.class.isAssignableFrom(mainClass)) {
            throw new Exception("主类 " + addonInfo.getMain() + " 必须继承 FotiaChatAddon");
        }

        return (FotiaChatAddon) mainClass.getDeclaredConstructor().newInstance();
    }

    /**
     * 从当前Addon的jar中获取资源（不查找父类加载器）
     */
    public InputStream getAddonResource(String name) {
        URL url = findResource(name);
        if (url != null) {
            try {
                return url.openStream();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
