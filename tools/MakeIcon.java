import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Renders the icons (the view icon is the same drawing as icons/ehttpeditor.svg).
 * Usage: java tools/MakeIcon.java <ehttpeditor|ehttpeditor-running|ehttpeditor-done|run|success|failure|environment> <size> <file.png>
 */
public class MakeIcon {

	private static final Color VIOLET = new Color(0x8b5cf6);
	private static final Color CYAN = new Color(0x22d3ee);
	private static final Color DARK = new Color(0x16161e);
	private static final Color ORANGE = new Color(0xff9e64);
	private static final Color GREEN = new Color(0x4ade80);
	private static final Color RED = new Color(0xf7768e);

	public static void main(String[] args) throws Exception {
		String name = args[0];
		int size = Integer.parseInt(args[1]);
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.scale(size / 16.0, size / 16.0);
		switch (name) {
		case "ehttpeditor", "ehttpeditor-running", "ehttpeditor-done" -> {
			exchange(g);
			if (!name.equals("ehttpeditor")) {
				// Status badge in the lower right corner, with a dark ring to detach it from the icon.
				g.setPaint(DARK);
				g.fill(new Ellipse2D.Double(7.6, 7.6, 8.4, 8.4));
				g.setPaint(name.equals("ehttpeditor-done") ? GREEN : ORANGE);
				g.fill(new Ellipse2D.Double(8.8, 8.8, 6, 6));
			}
		}
		case "run" -> {
			Path2D triangle = new Path2D.Double();
			triangle.moveTo(4.5, 2.5);
			triangle.lineTo(13, 8);
			triangle.lineTo(4.5, 13.5);
			triangle.closePath();
			g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setPaint(GREEN);
			g.fill(triangle);
			g.draw(triangle);
		}
		case "success" -> {
			g.setPaint(GREEN);
			g.fill(new Ellipse2D.Double(1.5, 1.5, 13, 13));
			g.setPaint(DARK);
			g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			Path2D check = new Path2D.Double();
			check.moveTo(4.8, 8.2);
			check.lineTo(7.1, 10.4);
			check.lineTo(11.3, 5.8);
			g.draw(check);
		}
		case "failure" -> {
			g.setPaint(RED);
			g.fill(new Ellipse2D.Double(1.5, 1.5, 13, 13));
			g.setPaint(DARK);
			g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(new Line2D.Double(5.5, 5.5, 10.5, 10.5));
			g.draw(new Line2D.Double(10.5, 5.5, 5.5, 10.5));
		}
		case "environment" -> {
			// A globe: the environments are the servers the requests go to.
			g.setPaint(CYAN);
			g.setStroke(new BasicStroke(1.3f));
			g.draw(new Ellipse2D.Double(2, 2, 12, 12));
			g.draw(new Arc2D.Double(5, 2, 6, 12, 0, 360, Arc2D.OPEN));
			g.draw(new Line2D.Double(2.3, 8, 13.7, 8));
			g.setPaint(ORANGE);
			g.setStroke(new BasicStroke(1.1f));
			g.draw(new Line2D.Double(3.4, 5, 12.6, 5));
			g.draw(new Line2D.Double(3.4, 11, 12.6, 11));
		}
		default -> throw new IllegalArgumentException(name);
		}
		g.dispose();
		ImageIO.write(image, "png", new File(args[2]));
	}

	/** The frame of the icons of RoiSoleil, with a request (orange, to the right) and its response (cyan, back). */
	private static void exchange(Graphics2D g) {
		g.setPaint(new GradientPaint(0, 1, VIOLET, 16, 15, CYAN));
		g.fill(new RoundRectangle2D.Double(0.5, 1.5, 15, 13, 6, 6));
		g.setPaint(DARK);
		g.fill(new RoundRectangle2D.Double(1.5, 2.5, 13, 11, 4.4, 4.4));
		g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		// The request.
		g.setPaint(ORANGE);
		g.draw(new Line2D.Double(4, 6, 11.4, 6));
		Path2D right = new Path2D.Double();
		right.moveTo(9.6, 4.3);
		right.lineTo(11.6, 6);
		right.lineTo(9.6, 7.7);
		g.draw(right);
		// The response.
		g.setPaint(CYAN);
		g.draw(new Line2D.Double(12, 10, 4.6, 10));
		Path2D left = new Path2D.Double();
		left.moveTo(6.4, 8.3);
		left.lineTo(4.4, 10);
		left.lineTo(6.4, 11.7);
		g.draw(left);
	}
}
