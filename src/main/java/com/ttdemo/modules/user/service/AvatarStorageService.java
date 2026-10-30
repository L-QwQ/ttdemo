package com.ttdemo.modules.user.service;

import com.ttdemo.common.api.ResultCode;
import com.ttdemo.common.exception.BizException;
import com.ttdemo.config.UploadProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 头像本地存储：按 avatar/年月/ 分目录，文件名用 UUID，扩展名由文件头推断（不信任原始文件名）。
 */
@Service
public class AvatarStorageService {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorageService.class);
    private static final String SUB_DIR = "avatar";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final long MIN_BYTES = 64;

    private final UploadProperties props;

    public AvatarStorageService(UploadProperties props) {
        this.props = props;
    }

    /**
     * 保存头像文件，返回可直接用于 img src 的相对地址。
     */
    public String store(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请选择头像图片");
        }
        if (file.getSize() > props.getMaxSize()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "图片不能超过 " + (props.getMaxSize() / 1024 / 1024) + "MB");
        }
        if (file.getSize() < MIN_BYTES) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片内容为空或已损坏");
        }
        byte[] head = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片读取失败");
        }
        String ext = detectExtension(head, read);
        if (ext == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅支持 JPG / PNG / WEBP 格式的图片");
        }

        String month = LocalDate.now().format(MONTH);
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = root().resolve(SUB_DIR).resolve(month);
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(root())) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法的存储路径");
        }
        try {
            Files.createDirectories(dir);
            file.transferTo(target);
        } catch (IOException e) {
            log.error("保存头像失败 userId={}", userId, e);
            throw new BizException(ResultCode.ERROR, "头像保存失败，请稍后重试");
        }
        return props.getUrlPrefix() + "/" + SUB_DIR + "/" + month + "/" + name;
    }

    /**
     * 仅删除本服务管理的旧头像，外链或空值直接忽略。
     */
    public void deleteIfManaged(String url) {
        String prefix = props.getUrlPrefix() + "/";
        if (!StringUtils.hasText(url) || !url.startsWith(prefix)) {
            return;
        }
        String relative = url.substring(prefix.length());
        Path target = root().resolve(relative).normalize();
        if (!target.startsWith(root())) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("删除旧头像失败 path={}", target, e);
        }
    }

    private Path root() {
        return Paths.get(props.getDir()).toAbsolutePath().normalize();
    }

    /** 通过文件头魔数判断真实类型，避免伪造 Content-Type 上传脚本 */
    private String detectExtension(byte[] head, int len) {
        if (len >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (len >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "png";
        }
        if (len >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
