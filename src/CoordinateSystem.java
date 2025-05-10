import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Rectangle2D;
import java.util.List;

public class CoordinateSystem extends JComponent {
	private static final int BASE_UNIT = 16;
    private static final Color GRID_COLOR = new Color(200, 200, 200,100);
    private static final Color AXIS_COLOR = new Color(80, 80, 80);
    
    private ChunkProvider chunkProvider;
    
    private double scale = 1.0;
    private Point offset = new Point(0, 0);
    private Point lastDragPoint;
    private Point selectedWorldCoord = null;
    
    // 缩放限制
    private static final double MIN_SCALE = 0.25;
    private static final double MAX_SCALE = 10.0;

    public CoordinateSystem() {
    	addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                // 只在首次获得有效尺寸时初始化原点
                if (getWidth() > 0 && getHeight() > 0 && offset.equals(new Point(0, 0))) {
                    setOffset(new Point(getWidth()/2, getHeight()/2));
                }
            }
        });
    	addMouseWheelListener(e -> handleMouseWheel(e));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastDragPoint = e.getPoint();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    // 转换坐标：屏幕坐标 → 世界坐标
                	int worldX = (int) Math.floor((e.getX() - offset.x) / (BASE_UNIT * scale));
                    int worldZ = (int) Math.floor((e.getY() - offset.y) / (BASE_UNIT * scale));
                    if (selectedWorldCoord != null && selectedWorldCoord.x == worldX && selectedWorldCoord.y == worldZ) {
                        selectedWorldCoord = null;
                    } else {
                        // 否则，更新选中的坐标
                        selectedWorldCoord = new Point(worldX, worldZ);
                    }
                    repaint();
                }
            }
        });
        addMouseMotionListener(new MouseInputAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Point current = e.getPoint();
                int dx = current.x - lastDragPoint.x;
                int dy = current.y - lastDragPoint.y;
                offset.translate(dx, dy);
                lastDragPoint = current;
                repaint();
            }
        });
    }
    
    public double getScale() {
        return scale;
    }
    
    public Point getOffset() {
        return new Point(offset);
    }

    public interface ChunkProvider {
        List<ChunkData> getVisibleChunks(double left, double right, double top, double bottom);
    }
    

    public static class ChunkData {
        public final int x;      // 方块坐标起点X
        public final int z;      // 方块坐标起点Z
        public final int width;  // 宽度（方块单位）
        public final int height; // 高度（方块单位）
        public final Color color;

        public ChunkData(int x, int z, int width, int height, Color color) {
            this.x = x;
            this.z = z;
            this.width = width;
            this.height = height;
            this.color = color;
        }
    }
    

    public void setChunkProvider(ChunkProvider provider) {
        this.chunkProvider = provider;
    }
    
    private int visibleLeft, visibleRight, visibleTop, visibleBottom;

    public void setVisibleArea(int left, int right, int top, int bottom) {
        this.visibleLeft = left;
        this.visibleRight = right;
        this.visibleTop = top;
        this.visibleBottom = bottom;
        revalidate();
        repaint();
    }

    Rectangle visRect = new Rectangle(0, 0, getWidth(), getHeight());

    public void setOffset(Point offset) {
        this.offset = offset;
        revalidate();
        repaint();
    }
    
    private void handleMouseWheel(MouseWheelEvent e) {
        double oldScale = scale;
        double scaleFactor = e.getWheelRotation() < 0 ? 1.1 : 0.9;
        setScale(scale * scaleFactor);
        
        // 保持缩放中心点
        Point point = e.getPoint();
        offset.x = (int) (point.x - (point.x - offset.x) * (scale / oldScale));
        offset.y = (int) (point.y - (point.y - offset.y) * (scale / oldScale));
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();

        // 1. 绘制白色背景
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // 2. 绘制区块（先于网格和坐标轴）
        drawChunks(g2d);

        // 3. 绘制网格
        drawGrid(g2d);

        // 4. 最后绘制坐标轴（保证在最上层）
        drawAxis(g2d); 
        
        if (selectedWorldCoord != null) {
            drawSelectedCoord(g);
        }
        
        drawDebugInfo(g2d);
        
        g2d.dispose();
    }
    
    private void drawDebugInfo(Graphics2D g) {
    	// 设置字体
        Font debugFont = new Font("微软雅黑", Font.PLAIN, 12);
        g.setFont(debugFont);
        g.setColor(Color.BLACK);
        
        // 获取当前缩放倍数和视点坐标
        String debugText = String.format("缩放倍数: %.2f, 视点坐标: (%d, %d)", scale, offset.x, offset.y);
        
        // 绘制调试信息
        g.drawString(debugText, 10, getHeight() - 10);  // 距离底部10像素位置
    }
    

    private void drawGrid(Graphics2D g) {
        g.setColor(GRID_COLOR);
        
        Rectangle visRect = getVisibleRect();
        double[] visibleArea = calculateVisibleArea(visRect);
        
        int step = calculateGridStep();
        int startX = (int) (Math.floor(visibleArea[0] / step) * step);
        int endX = (int) (Math.ceil(visibleArea[1] / step) * step);
        int startZ = (int) (Math.floor(visibleArea[2] / step) * step);
        int endZ = (int) (Math.ceil(visibleArea[3] / step) * step);

        // 绘制水平网格
        for (int x = startX; x <= endX; x += step) {
            int screenX = (int) (x * BASE_UNIT * scale + offset.x);
            drawDashedLine(g, screenX, 0, screenX, getHeight());
        }

        // 绘制垂直网格
        for (int z = startZ; z <= endZ; z += step) {
            int screenZ = (int) (z * BASE_UNIT * scale + offset.y);
            drawDashedLine(g, 0, screenZ, getWidth(), screenZ);
        }
    }
    

    private void drawAxis(Graphics2D g) {
        g.setColor(AXIS_COLOR);
        Stroke axisStroke = new BasicStroke(2);
        g.setStroke(axisStroke);
        
        // 计算原点在屏幕上的位置
        int originX = (int) offset.x;
        int originZ = (int) offset.y;
        
        // 绘制X轴（水平轴）
        g.drawLine(0, originZ, getWidth(), originZ);
        
        // 绘制Z轴（垂直轴）
        g.drawLine(originX, 0, originX, getHeight());
        
        // 绘制刻度
        drawAxisTicks(g, originX, originZ);
    }

    private void drawAxisTicks(Graphics2D g, int originX, int originZ) {
    	Font labelFont = new Font("Arial", Font.BOLD, 14); // 选择加粗且更大的字体
        g.setFont(labelFont);
        
        Rectangle visRect = getVisibleRect();
        double[] visibleArea = calculateVisibleArea(visRect);
        int step = calculateGridStep();
        
        // X轴刻度
        int startX = (int) (Math.floor(visibleArea[0] / step) * step);
        int endX = (int) (Math.ceil(visibleArea[1] / step) * step);
        for (int x = startX; x <= endX; x += step) {
            int screenX = (int) (x * BASE_UNIT * scale + offset.x);
            g.drawLine(screenX, originZ - 3, screenX, originZ + 3);
            drawCenteredString(g, String.valueOf(x * 16), screenX, originZ + 15);
        }

        // Z轴刻度
        int startZ = (int) (Math.floor(visibleArea[2] / step) * step);
        int endZ = (int) (Math.ceil(visibleArea[3] / step) * step);
        for (int z = startZ; z <= endZ; z += step) {
            int screenZ = (int) (z * BASE_UNIT * scale + offset.y);
            g.drawLine(originX - 3, screenZ, originX + 3, screenZ);
            drawCenteredString(g, String.valueOf(z * 16), originX - 10, screenZ - 5);
        }
    }
    
    private void drawSelectedCoord(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setColor(new Color(255, 0, 0, 100));
        
        // 计算选中区块的屏幕坐标
        int x = (int) (selectedWorldCoord.x * 16 * scale + offset.x);
        int z = (int) (selectedWorldCoord.y * 16 * scale + offset.y);
        int size = (int) (16 * scale);
        
        // 绘制半透明红色覆盖层
        g2d.fillRect(x, z, size, size);
        
        g2d.setColor(new Color(100, 100, 100)); // 灰色字体
        g2d.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        
        // 绘制坐标文本
        String coordText = String.format("已选中区域坐标：(%d, %d) 区块坐标：(%d,%d)", 
                selectedWorldCoord.x * 16, 
                selectedWorldCoord.y * 16,
                selectedWorldCoord.x,
                selectedWorldCoord.y
                );
            
        g2d.drawString(coordText, 10, 20);
    }

    private void drawDashedLine(Graphics g, int x1, int y1, int x2, int y2) {
        Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                0, new float[]{4}, 0);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setStroke(dashed);
        g2d.drawLine(x1, y1, x2, y2);
        g2d.dispose();
    }

    private void drawCenteredString(Graphics g, String text, int x, int y) {
        FontMetrics fm = g.getFontMetrics();
        int width = fm.stringWidth(text);
        g.drawString(text, x - width/2, y);
    }

    private int calculateGridStep() {
        double scaledUnit = BASE_UNIT * scale;
        if (scaledUnit > 128) return 1;
        if (scaledUnit > 64) return 2;
        if (scaledUnit > 32) return 4;
        return 8;
    }

    private double[] calculateVisibleArea(Rectangle visRect) {
        return new double[] {
            (visRect.x - offset.x) / (BASE_UNIT * scale),
            (visRect.x + visRect.width - offset.x) / (BASE_UNIT * scale),
            (visRect.y - offset.y) / (BASE_UNIT * scale),
            (visRect.y + visRect.height - offset.y) / (BASE_UNIT * scale)
        };
    }

    private void drawChunks(Graphics2D g) {
        Rectangle visRect = getVisibleRect();
        double[] worldBounds = {
            (visRect.x - offset.x) / scale,    // left
            (visRect.x + visRect.width - offset.x) / scale, // right
            (visRect.y - offset.y) / scale,    // top
            (visRect.y + visRect.height - offset.y) / scale  // bottom
        };

        for (ChunkData chunk : chunkProvider.getVisibleChunks(
            worldBounds[0], worldBounds[1], worldBounds[2], worldBounds[3])) 
        {
        	double screenX = offset.x + (chunk.x / 16) * 16 * scale; // chunk.x为方块坐标需转区块坐标
        	double screenZ = offset.y + (chunk.z / 16) * 16 * scale;
        	double screenW = chunk.width * BASE_UNIT * scale / 16.0;
        	double screenH = chunk.height * BASE_UNIT * scale / 16.0;
        	
            // 抗锯齿处理（保持图片中的清晰边缘）
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            
            // 绘制填充区域（完全覆盖16x16网格）
            g.setColor(chunk.color);
            g.fillRect((int)screenX, (int)screenZ, (int)Math.ceil(screenW), (int)Math.ceil(screenH));
            
            // 绘制细边框（与图片中的灰色线条一致）
            g.setColor(new Color(80, 80, 80, 30));
            g.drawRect((int)screenX, (int)screenZ, (int)screenW, (int)screenH);
        }
    }

    public void setScale(double newScale) {
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        revalidate();
        repaint();
    }
    
    public Point worldToScreen(int worldX, int worldZ) {
        return new Point(
            (int)(worldX * scale) + offset.x,
            (int)(worldZ * scale) + offset.y
        );
    }
    
    public void setSelectedChunk(int chunkX, int chunkZ) {
        this.selectedWorldCoord = new Point(chunkX, chunkZ);
    }

    public void deselect() {
    	if (selectedWorldCoord != null) {
    		selectedWorldCoord = null;
        	repaint();
    	}
    }

}
