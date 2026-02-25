package gg.fotia.chat.api;

import java.util.List;

/**
 * Addon信息类
 * 从addon.yml中读取
 */
public class AddonInfo {

    private final String name;
    private final String version;
    private final String main;
    private final String author;
    private final String description;
    private final List<String> depend;
    private final List<String> softDepend;

    public AddonInfo(String name, String version, String main, String author,
                     String description, List<String> depend, List<String> softDepend) {
        this.name = name;
        this.version = version;
        this.main = main;
        this.author = author;
        this.description = description;
        this.depend = depend;
        this.softDepend = softDepend;
    }

    /**
     * 获取Addon名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取Addon版本
     */
    public String getVersion() {
        return version;
    }

    /**
     * 获取Addon主类路径
     */
    public String getMain() {
        return main;
    }

    /**
     * 获取Addon作者
     */
    public String getAuthor() {
        return author;
    }

    /**
     * 获取Addon描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取依赖的Addon列表
     */
    public List<String> getDepend() {
        return depend;
    }

    /**
     * 获取软依赖的Addon列表
     */
    public List<String> getSoftDepend() {
        return softDepend;
    }

    @Override
    public String toString() {
        return "AddonInfo{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", main='" + main + '\'' +
                ", author='" + author + '\'' +
                '}';
    }
}
