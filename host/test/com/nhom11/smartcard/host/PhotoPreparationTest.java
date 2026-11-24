package com.nhom11.smartcard.host;
import com.nhom11.smartcard.host.provider.PhotoTransfer;
import com.nhom11.smartcard.host.service.PhotoPreparation;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;

public final class PhotoPreparationTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("photo-test");
        BufferedImage source=new BufferedImage(600,900,BufferedImage.TYPE_INT_RGB);
        Random r=new Random(4);
        for(int y=0;y<source.getHeight();y++) for(int x=0;x<source.getWidth();x++) source.setRGB(x,y,r.nextInt(0x1000000));
        Path input=dir.resolve("fake.png"); ImageIO.write(source,"png",input.toFile());
        byte[] jpeg=PhotoPreparation.prepare(input);
        if(jpeg.length==0 || jpeg.length>PhotoTransfer.MAX_SIZE) throw new AssertionError("size");
        BufferedImage output=ImageIO.read(new ByteArrayInputStream(jpeg));
        if(output==null || output.getWidth()>256 || output.getHeight()>256) throw new AssertionError("decode/size");
        if(Math.abs((double)output.getWidth()/output.getHeight()-2.0/3.0)>0.02) throw new AssertionError("aspect ratio");
        Path invalid=dir.resolve("invalid.png"); Files.writeString(invalid,"not an image");
        try { PhotoPreparation.prepare(invalid); throw new AssertionError("invalid accepted"); } catch(java.io.IOException expected) {}
        try { PhotoPreparation.prepare(dir.resolve("missing.png")); throw new AssertionError("missing accepted"); } catch(java.io.IOException expected) {}
        System.out.println("PhotoPreparationTest: 5 checks passed (generated data only).");
    }
}
