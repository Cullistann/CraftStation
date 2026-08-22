package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PixelIcons {

    // Load a Minecraft item texture from assets/items/ and scale it to desired size
    private static Icon loadItemIcon(String filename, int targetSize) {
        try {
            BufferedImage img = AssetManager.loadImage("items/" + filename);
            if (img != null) {
                // Scale with nearest-neighbor to preserve pixel art crispness
                BufferedImage scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(img, 0, 0, targetSize, targetSize, null);
                g.dispose();
                return new ImageIcon(scaled);
            }
        } catch (Exception e) {
            System.err.println("Failed to load icon: " + filename);
        }
        // Return a fallback empty icon
        return new ImageIcon(new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB));
    }

    private static Icon loadBlockIcon(String filename, int targetSize) {
        try {
            BufferedImage img = AssetManager.loadImage("blocks/" + filename);
            if (img != null) {
                BufferedImage scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(img, 0, 0, targetSize, targetSize, null);
                g.dispose();
                return new ImageIcon(scaled);
            }
        } catch (Exception ignored) {}
        return new ImageIcon(new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB));
    }

    // Steve head: crop just the face from the full steve.png skin texture
    private static Icon loadSteveHead(int targetSize) {
        try {
            BufferedImage img = AssetManager.loadImage("items/steve.png");
            if (img != null) {
                // Steve skin: face is at x=8, y=8, size=8x8 in a 64x32 (or 64x64) skin
                BufferedImage face = img.getSubimage(8, 8, 8, 8);
                BufferedImage scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(face, 0, 0, targetSize, targetSize, null);
                g.dispose();
                return new ImageIcon(scaled);
            }
        } catch (Exception e) {
            System.err.println("Failed to load steve head");
        }
        return new ImageIcon(new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB));
    }

    // === Dashboard Stat Card Icons ===

    // TPS -> Minecraft Gold Clock
    public static Icon createGoldClockIcon(int scale) {
        return loadItemIcon("clock.png", 8 * scale);
    }

    // Players -> Steve Head (cropped from skin)
    public static Icon createSteveIcon(int scale) {
        return loadSteveHead(8 * scale);
    }

    // Uptime -> Minecraft Compass
    public static Icon createSilverClockIcon(int scale) {
        return loadItemIcon("compass.png", 8 * scale);
    }

    // RAM -> Redstone Dust
    public static Icon createRamIcon(int scale) {
        return loadItemIcon("redstone.png", 8 * scale);
    }

    // === Button Icons ===

    // Start -> Emerald (green gem = go!)
    public static Icon createPlayIcon(int size) {
        return loadItemIcon("emerald.png", size + 4);
    }

    // Stop -> Barrier block (red circle with line = stop)
    public static Icon createStopIcon(int size) {
        return loadItemIcon("barrier.png", size + 4);
    }

    // Restart -> Ender Pearl (teleport/reset)
    public static Icon createSyncIcon(int size) {
        return loadItemIcon("ender_pearl.png", size + 4);
    }

    // Checkmark -> Experience Bottle (green sparkle = success)
    public static Icon createCheckmarkIcon(int scale) {
        int size = 8 * scale;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        g.setColor(new Color(58, 58, 58));
        g.fillRect(0, 0, size, size);
        g.setColor(new Color(104, 104, 104));
        g.drawRect(0, 0, size - 1, size - 1);
        g.setColor(new Color(28, 28, 28));
        g.drawRect(1, 1, size - 3, size - 3);

        int thick = Math.max(2, size / 8);
        g.setStroke(new BasicStroke(thick, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.setColor(new Color(26, 170, 66));
        int x1 = Math.max(1, size / 5);
        int y1 = size / 2;
        int x2 = size / 2 - Math.max(1, size / 10);
        int y2 = size - Math.max(2, size / 4);
        int x3 = size - Math.max(2, size / 5);
        int y3 = Math.max(2, size / 5);
        g.drawLine(x1, y1, x2, y2);
        g.drawLine(x2, y2, x3, y3);
        g.dispose();
        return new ImageIcon(img);
    }

    // Server Settings -> Experience Bottle
    public static Icon createExperienceBottleIcon(int scale) {
        return loadItemIcon("experience_bottle.png", 8 * scale);
    }
}
