import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Writes the icons of the launchers of the standalone application from the PNG of the application (drawn by
 * MakeIcon.java): ehttpeditor.ico (Windows), ehttpeditor.icns (macOS) and ehttpeditor.xpm (Linux).
 * Usage: java tools/MakeLauncherIcons.java <folder of ehttpeditor16.png...ehttpeditor256.png> <output folder>
 */
public class MakeLauncherIcons {

	public static void main(String[] args) throws Exception {
		File in = new File(args[0]);
		File out = new File(args[1]);
		Files.write(new File(out, "ehttpeditor.ico").toPath(), ico(in));
		Files.write(new File(out, "ehttpeditor.icns").toPath(), icns(in));
		xpm(read(in, 128), new File(out, "ehttpeditor.xpm"));
	}

	private static BufferedImage read(File folder, int size) throws IOException {
		return ImageIO.read(new File(folder, "ehttpeditor" + size + ".png"));
	}

	/**
	 * The launcher of Windows only takes the icons of the same size and depth as its own ones: 16, 32 and 48
	 * pixels in 256 colors and in 32 bits, and 256 pixels in 32 bits, all as bitmaps.
	 */
	private static byte[] ico(File folder) throws IOException {
		List<byte[]> images = new ArrayList<>();
		List<int[]> formats = new ArrayList<>();
		for (int depth : new int[] { 8, 32 }) {
			for (int size : new int[] { 16, 32, 48, 256 }) {
				if (depth == 8 && size == 256) {
					continue;
				}
				images.add(bitmap(read(folder, size), depth));
				formats.add(new int[] { size, depth });
			}
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		LittleEndian le = new LittleEndian(bytes);
		le.short16(0);
		le.short16(1);
		le.short16(images.size());
		int offset = 6 + 16 * images.size();
		for (int i = 0; i < images.size(); i++) {
			int size = formats.get(i)[0];
			bytes.write(size == 256 ? 0 : size);
			bytes.write(size == 256 ? 0 : size);
			bytes.write(0); // 256 colors or more
			bytes.write(0);
			le.short16(1);
			le.short16(formats.get(i)[1]);
			le.int32(images.get(i).length);
			le.int32(offset);
			offset += images.get(i).length;
		}
		for (byte[] image : images) {
			bytes.write(image);
		}
		return bytes.toByteArray();
	}

	/** A bitmap of an icon: the header, the palette (8 bits), the pixels and the mask, from the bottom. */
	private static byte[] bitmap(BufferedImage image, int depth) {
		int size = image.getWidth();
		// 8 bits: the colors reduced to 5 bits a channel, or fewer until they are not more than 256
		int shift = 3;
		Map<Integer, Integer> palette = palette(image, shift);
		while (depth == 8 && palette.size() > 256) {
			palette = palette(image, ++shift);
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		LittleEndian le = new LittleEndian(bytes);
		le.int32(40);
		le.int32(size);
		le.int32(size * 2);
		le.short16(1);
		le.short16(depth);
		le.int32(0);
		le.int32(0);
		le.int32(0);
		le.int32(0);
		le.int32(depth == 8 ? 256 : 0);
		le.int32(0);
		if (depth == 8) {
			List<Integer> colors = new ArrayList<>(palette.keySet());
			for (int i = 0; i < 256; i++) {
				le.int32(i < colors.size() ? colors.get(i) : 0);
			}
		}
		int rowBytes = (size * depth / 8 + 3) / 4 * 4;
		for (int y = size - 1; y >= 0; y--) {
			int written = 0;
			for (int x = 0; x < size; x++) {
				int argb = image.getRGB(x, y);
				if (depth == 8) {
					bytes.write(palette.get(reduce(argb, shift)));
					written++;
				} else {
					le.int32(argb);
					written += 4;
				}
			}
			for (; written < rowBytes; written++) {
				bytes.write(0);
			}
		}
		// The mask: the transparent pixels (the 8 bits icons have no alpha)
		int maskBytes = (size + 31) / 32 * 4;
		for (int y = size - 1; y >= 0; y--) {
			byte[] row = new byte[maskBytes];
			for (int x = 0; x < size; x++) {
				if (image.getRGB(x, y) >>> 24 < 128) {
					row[x / 8] |= (byte) (0x80 >> x % 8);
				}
			}
			bytes.writeBytes(row);
		}
		return bytes.toByteArray();
	}

	/** The index of each color of the image, reduced to 8 - shift bits a channel. */
	private static Map<Integer, Integer> palette(BufferedImage image, int shift) {
		Map<Integer, Integer> palette = new LinkedHashMap<>();
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				palette.putIfAbsent(reduce(image.getRGB(x, y), shift), palette.size());
			}
		}
		return palette;
	}

	/** The color without its lowest bits, black for the transparent pixels. */
	private static int reduce(int argb, int shift) {
		int mask = 0xff << shift & 0xff;
		return argb >>> 24 < 128 ? 0 : (argb >> 16 & mask) << 16 | (argb >> 8 & mask) << 8 | argb & mask;
	}

	/** The PNG of each size, with the type of the ICNS format. */
	private static byte[] icns(File folder) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		DataOutputStream data = new DataOutputStream(body);
		String[][] types = { { "icp4", "16" }, { "icp5", "32" }, { "icp6", "64" }, { "ic07", "128" }, { "ic08", "256" } };
		for (String[] type : types) {
			byte[] png = Files.readAllBytes(new File(folder, "ehttpeditor" + type[1] + ".png").toPath());
			data.writeBytes(type[0]);
			data.writeInt(png.length + 8);
			data.write(png);
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream file = new DataOutputStream(bytes);
		file.writeBytes("icns");
		file.writeInt(body.size() + 8);
		body.writeTo(file);
		return bytes.toByteArray();
	}

	/** The XPM of the launcher of Linux: two characters a color, the transparent pixels are "None". */
	private static void xpm(BufferedImage image, File file) throws IOException {
		int size = image.getWidth();
		Map<Integer, String> codes = new LinkedHashMap<>();
		String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+-";
		String[] rows = new String[size];
		for (int y = 0; y < size; y++) {
			StringBuilder row = new StringBuilder();
			for (int x = 0; x < size; x++) {
				int color = reduce(image.getRGB(x, y), 2);
				int index = codes.size();
				row.append(codes.computeIfAbsent(color,
						c -> c == 0 ? "  " : "" + chars.charAt(index / chars.length()) + chars.charAt(index % chars.length())));
			}
			rows[y] = row.toString();
		}
		try (PrintWriter writer = new PrintWriter(file, "US-ASCII")) {
			writer.println("/* XPM */");
			writer.println("static char *ehttpeditor[] = {");
			writer.println("\"" + size + " " + size + " " + codes.size() + " 2\",");
			codes.forEach((color, code) -> writer.println("\"" + code + " c " + (color == 0 && code.equals("  ")
					? "None" : String.format("#%06x", color)) + "\","));
			for (int y = 0; y < size; y++) {
				writer.println("\"" + rows[y] + "\"" + (y < size - 1 ? "," : ""));
			}
			writer.println("};");
		}
	}

	private record LittleEndian(ByteArrayOutputStream bytes) {

		void short16(int value) {
			bytes.write(value & 0xff);
			bytes.write(value >> 8 & 0xff);
		}

		void int32(int value) {
			short16(value & 0xffff);
			short16(value >>> 16);
		}
	}
}
