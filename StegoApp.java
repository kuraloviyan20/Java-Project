import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Scanner;
import javax.imageio.ImageIO;

abstract class SteganographyProcessor {
    public abstract void process(String inputImage, String outputImage, String message) throws Exception;
}

class ImageFile {
    private String filePath;
    private BufferedImage image;

    public ImageFile(String filePath) {
        this.filePath = filePath;
    }

    public void load() throws Exception {
        File f = new File(filePath);
        if (!f.exists()) throw new Exception("File not found: " + filePath);
        image = ImageIO.read(f);
        if (image == null) throw new Exception("Unable to read image (unsupported format): " + filePath);
    }

    public void save(String destPath) throws Exception {
        File out = new File(destPath);
        ImageIO.write(image, "png", out);
    }

    public BufferedImage getImage() {
        return image;
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }
}

class Encoder extends SteganographyProcessor {

    @Override
    public void process(String inputImage, String outputImage, String message) throws Exception {
        ImageFile imgFile = new ImageFile(inputImage);
        imgFile.load();
        BufferedImage img = imgFile.getImage();
        int width = img.getWidth();
        int height = img.getHeight();
        int capacityBits = width * height; // one bit per pixel (blue LSB)
        byte[] msgBytes = (message + "~").getBytes("UTF-8"); // append stop marker
        int requiredBits = msgBytes.length * 8;

        if (requiredBits > capacityBits) {
            throw new Exception("Message too large. Capacity (bytes): " + (capacityBits / 8) +
                                " , required bytes: " + msgBytes.length);
        }

        int msgIndex = 0;
        int bitIndex = 0;

        outer:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int blue = rgb & 0xFF;
                int bit = (msgBytes[msgIndex] >> (7 - bitIndex)) & 1;
                int newBlue = (blue & 0xFE) | bit; // set LSB of blue
                int newRgb = (rgb & 0xFFFFFF00) | newBlue;
                img.setRGB(x, y, newRgb);

                bitIndex++;
                if (bitIndex == 8) {
                    bitIndex = 0;
                    msgIndex++;
                    if (msgIndex >= msgBytes.length) break outer;
                }
            }
        }

        // save as PNG to preserve data
        ImageIO.write(img, "png", new File(outputImage));
        System.out.println("[✔] Encoding finished. Saved to: " + outputImage);
    }
}

class Decoder extends SteganographyProcessor {

    @Override
    public void process(String inputImage, String outputImage, String message) throws Exception {
        ImageFile imgFile = new ImageFile(inputImage);
        imgFile.load();
        BufferedImage img = imgFile.getImage();
        int width = img.getWidth();
        int height = img.getHeight();

        StringBuilder sb = new StringBuilder();
        int currentByte = 0;
        int bitCount = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int blue = rgb & 0xFF;
                int bit = blue & 1;
                currentByte = (currentByte << 1) | bit;
                bitCount++;

                if (bitCount == 8) {
                    char c = (char) currentByte;
                    if (c == '~') {
                        System.out.println("[✔] Decoded message: " + sb.toString());
                        return;
                    }
                    sb.append(c);
                    currentByte = 0;
                    bitCount = 0;
                }
            }
        }

        System.out.println("[!] No termination marker found. Partial message (if any): " + sb.toString());
    }
}

public class StegoApp {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        try {
            System.out.println("===== Image Steganography =====");
            System.out.println("1. Encode Message");
            System.out.println("2. Decode Message");
            System.out.print("Choose option (1/2): ");
            int choice = -1;
            try {
                choice = Integer.parseInt(sc.nextLine().trim());
            } catch (Exception ex) {
                System.out.println("Invalid input. Exiting.");
                sc.close();
                return;
            }

            if (choice == 1) {
                System.out.print("Enter input image filename (PNG recommended): ");
                String inputImage = sc.nextLine().trim();
                System.out.print("Enter output image filename (will be saved as PNG): ");
                String outputImage = sc.nextLine().trim();
                System.out.print("Enter secret message: ");
                String message = sc.nextLine();

                Encoder encoder = new Encoder();
                try {
                    encoder.process(inputImage, outputImage, message);
                } catch (Exception e) {
                    System.out.println("[Error] " + e.getMessage());
                }

            } else if (choice == 2) {
                System.out.print("Enter encoded image filename (PNG): ");
                String inputImage = sc.nextLine().trim();

                Decoder decoder = new Decoder();
                try {
                    decoder.process(inputImage, null, null);
                } catch (Exception e) {
                    System.out.println("[Error] " + e.getMessage());
                }

            } else {
                System.out.println("Invalid option. Exiting.");
            }
        } finally {
            sc.close();
        }
    }
}
