package com.nhom11.smartcard.host.service;

import com.nhom11.smartcard.host.provider.PhotoTransfer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/** Prepare non-sensitive demo images; no encryption, no connection to issuance yet. */
public final class PhotoPreparation {
    private static final long MAX_FILE = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000L;
    private PhotoPreparation() {}

    public static byte[] prepare(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file) || Files.size(file) > MAX_FILE)
            throw new IOException("Ảnh không tồn tại hoặc lớn hơn 10 MiB.");
        BufferedImage src;
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) throw new IOException("Không mở được ảnh.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("Định dạng ảnh không hợp lệ.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w=reader.getWidth(0), h=reader.getHeight(0);
                if (w <= 0 || h <= 0 || (long)w*h > MAX_PIXELS) throw new IOException("Ảnh vượt giới hạn kích thước điểm ảnh.");
                src=reader.read(0);
                if (src == null) throw new IOException("Không giải mã được ảnh.");
            } finally { reader.dispose(); }
        }
        for (int bound : new int[]{256,192,144,108,80}) {
            double scale=Math.min(1.0, (double)bound / Math.max(src.getWidth(), src.getHeight()));
            int w=Math.max(1,(int)Math.round(src.getWidth()*scale)), h=Math.max(1,(int)Math.round(src.getHeight()*scale));
            BufferedImage rgb = new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
            Graphics2D g=rgb.createGraphics();
            try { g.setColor(Color.WHITE); g.fillRect(0,0,w,h); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC); g.drawImage(src,0,0,w,h,null); } finally { g.dispose(); }
            for (float q : new float[]{0.85f,0.7f,0.55f,0.4f}) {
                byte[] bytes=jpeg(rgb,q);
                if (bytes.length <= PhotoTransfer.MAX_SIZE) return bytes;
            }
        }
        throw new IOException("Không thể nén ảnh vào 16384 byte.");
    }
    private static byte[] jpeg(BufferedImage image,float quality) throws IOException {
        Iterator<ImageWriter> it=ImageIO.getImageWritersByFormatName("jpeg");
        if (!it.hasNext()) throw new IOException("Không có bộ mã hóa JPEG.");
        ImageWriter writer=it.next();
        try (ByteArrayOutputStream bytes=new ByteArrayOutputStream(); ImageOutputStream out=ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out); ImageWriteParam p=writer.getDefaultWriteParam(); p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); p.setCompressionQuality(quality);
            writer.write(null,new javax.imageio.IIOImage(image,null,null),p); out.flush(); return bytes.toByteArray();
        } finally { writer.dispose(); }
    }
}
