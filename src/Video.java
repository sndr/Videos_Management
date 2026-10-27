import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Modelo de dados para um vídeo gerenciado no Dashboard.
 */
public class Video {
    private String id;
    private String title;
    private String category;
    private String duration;
    private long views;
    private long likes;
    private String status; // "Publicado", "Rascunho", "Privado", "Não Listado"
    private String uploadDate;
    private String description;
    private String videoUrl;

    public Video(String id, String title, String category, String duration, long views, long likes, String status, String description, String videoUrl) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.duration = duration;
        this.views = views;
        this.likes = likes;
        this.status = status;
        this.uploadDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        this.description = description;
        this.videoUrl = videoUrl;
    }

    public Video(String id, String title, String category, String duration, long views, long likes, String status, String uploadDate, String description, String videoUrl) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.duration = duration;
        this.views = views;
        this.likes = likes;
        this.status = status;
        this.uploadDate = uploadDate;
        this.description = description;
        this.videoUrl = videoUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public long getViews() { return views; }
    public void setViews(long views) { this.views = views; }

    public long getLikes() { return likes; }
    public void setLikes(long likes) { this.likes = likes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUploadDate() { return uploadDate; }
    public void setUploadDate(String uploadDate) { this.uploadDate = uploadDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getFormattedViews() {
        NumberFormat nf = NumberFormat.getInstance(Locale.forLanguageTag("pt-BR"));
        return nf.format(views);
    }

    public String getFormattedLikes() {
        NumberFormat nf = NumberFormat.getInstance(Locale.forLanguageTag("pt-BR"));
        return nf.format(likes);
    }
}
