import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SlimeChunkMapping extends JFrame {
    private final CoordinateSystem coordSystem = new CoordinateSystem();
    private long currentSeed;
    private JTextField seedField;
    private JTextField xField;
    private JTextField zField;

    public SlimeChunkMapping() {
        initUI();
        setupChunkProvider();
        generateRandomSeed();
        setupControls();
    }
    
    private void generateRandomSeed() {
        currentSeed = new Random().nextLong();
        if (seedField != null) {
            seedField.setText(Long.toString(currentSeed));
        }
    }

    private void initUI() {
        setTitle("Minecraft 史莱姆区块坐标分布");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout());
        
        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.weightx = 1;
        gbc.gridx = 0;

        JLabel authorLabel = new JLabel("Made by CreatorCSIE");
        JLabel versionLabel = new JLabel("版本：1.0");
        authorLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        versionLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));

        gbc.gridy = 0;
        infoPanel.add(authorLabel, gbc);
        gbc.gridy = 1;
        infoPanel.add(versionLabel, gbc);

        getContentPane().add(infoPanel, BorderLayout.SOUTH);
    }

    private void setupChunkProvider() {
        coordSystem.setChunkProvider((left, right, top, bottom) -> {
            // 根据图片坐标范围优化查询范围
        	final int MAX_CHUNK = 12550820 / 16;
            int minChunkX = (int) Math.floor(left / 16) - 1; // 扩展查询边界
            int maxChunkX = (int) Math.ceil(right / 16) + 1;
            int minChunkZ = (int) Math.floor(top / 16) - 1;
            int maxChunkZ = (int) Math.ceil(bottom / 16) + 1;

            ArrayList<CoordinateSystem.ChunkData> chunks = new ArrayList<>();
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                	if (Math.abs(chunkX) > MAX_CHUNK || Math.abs(chunkZ) > MAX_CHUNK) {
                        continue;
                    }
                    // 生成精确的16x16方块区域坐标（匹配图片中的-8,0区块）
                    int blockX = chunkX * 16;
                    int blockZ = chunkZ * 16;
                    chunks.add(new CoordinateSystem.ChunkData(
                        blockX, blockZ, 16, 16, // 严格对应16x16区域
                        isSlimeChunk(currentSeed, chunkX, chunkZ) 
                            ? new Color(100, 200, 100, 150) 
                            : new Color(200, 200, 200, 50)
                    ));
                }
            }
            return chunks;
        });
    }

    private void setupControls() {
        JPanel controlPanel = new JPanel();
        xField = new JTextField(8);
        zField = new JTextField(8);
        JButton searchButton = new JButton("搜索");
        searchButton.addActionListener(e -> searchChunk());

        controlPanel.add(new JLabel("X:"));
        controlPanel.add(xField);
        controlPanel.add(new JLabel("Z:"));
        controlPanel.add(zField);
        controlPanel.add(searchButton);
        
        seedField = new JTextField(15);
        seedField.setText(Long.toString(currentSeed)); // 初始显示随机种子
        
        JButton updateButton = new JButton("应用种子");
        JButton randomButton = new JButton("随机种子");

        updateButton.addActionListener(e -> updateSeed(seedField.getText()));
        randomButton.addActionListener(e -> {
            generateRandomSeed();
            coordSystem.deselect();
            ensureChunkVisible(0,0);
            coordSystem.repaint();
        });

        controlPanel.add(new JLabel("World Seed:"));
        controlPanel.add(seedField);
        controlPanel.add(updateButton);
        controlPanel.add(randomButton);

        getContentPane().add(controlPanel, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(coordSystem), BorderLayout.CENTER);
    }
    
    private void ensureChunkVisible(int chunkX, int chunkZ) {
        // 计算目标区块中心点（世界坐标，方块单位）
        int blockCenterX = chunkX * 16 + 8;
        int blockCenterZ = chunkZ * 16 + 8;

        // 获取当前缩放比例
        double scale = coordSystem.getScale();

        // 获取窗口大小（需要确保窗口和坐标系已布局完成）
        int panelWidth = coordSystem.getWidth();
        int panelHeight = coordSystem.getHeight();

        // 计算新的 offset，使目标点位于视图中央
        int newOffsetX = (int)(panelWidth / 2 - blockCenterX * scale);
        int newOffsetY = (int)(panelHeight / 2 - blockCenterZ * scale);

        // 设置新的偏移值
        coordSystem.setOffset(new Point(newOffsetX, newOffsetY));

        // 触发重绘
        coordSystem.repaint();
    }
    
    private void searchChunk() {
    	
    	coordSystem.deselect();
        try {
            int inputX = Integer.parseInt(xField.getText());
            int inputZ = Integer.parseInt(zField.getText());
            
            // 限制范围：chunkX / chunkZ 不得超过 784426
            final int MAX_CHUNK = 784426;
            final int MAX_BLOCK = MAX_CHUNK * 16;

            if (Math.abs(inputX) > MAX_BLOCK || Math.abs(inputZ) > MAX_BLOCK) {
                JOptionPane.showMessageDialog(this, "坐标超出支持范围（不得超过 ±" + MAX_BLOCK + "）");
                return;
            }

            int chunkX = inputX / 16;
            int chunkZ = inputZ / 16;

            // 设置模糊搜索范围（例如半径为10的区块）
            int searchRadius = 10;
            boolean found = false;
            int closestDistance = Integer.MAX_VALUE;
            Point closestChunk = null;

            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    int cx = chunkX + dx;
                    int cz = chunkZ + dz;
                    
                    if (Math.abs(cx) > MAX_CHUNK || Math.abs(cz) > MAX_CHUNK) {
                        continue; // 跳过非法区块
                    }

                    if (isSlimeChunk(currentSeed, cx, cz)) {
                        int distanceSquared = dx * dx + dz * dz;
                        if (distanceSquared < closestDistance) {
                            closestDistance = distanceSquared;
                            closestChunk = new Point(cx, cz);
                            found = true;
                        }
                    }
                }
            }

            if (found) {
                coordSystem.setSelectedChunk(closestChunk.x, closestChunk.y);
                ensureChunkVisible(closestChunk.x, closestChunk.y);
                coordSystem.repaint();
            } else {
                JOptionPane.showMessageDialog(this, "附近未找到史莱姆区块");
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "请输入有效的整数坐标");
        }
    }


    private void updateSeed(String seedText) {
        try {
            currentSeed = Long.parseLong(seedText);
            seedField.setText(Long.toString(currentSeed)); // 确保输入框同步
            coordSystem.deselect();
            ensureChunkVisible(0,0);
            coordSystem.repaint();
            System.out.println("Seed updated and view reset to origin.");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid seed format");
            seedField.setText(Long.toString(currentSeed)); // 恢复为有效种子
        }
    }

    private boolean isSlimeChunk(long worldSeed, int chunkX, int chunkZ) {
        final long MAGIC_SEED = 987234911L;
        long hash = worldSeed 
            + (chunkX * chunkX * 4987142L)
            + (chunkX * 5947611L)
            + (chunkZ * chunkZ * 4392871L)
            + (chunkZ * 389711L);
        hash = (hash ^ MAGIC_SEED);
        return new Random(hash).nextInt(10) == 0;
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            SlimeChunkMapping visualizer = new SlimeChunkMapping();
            visualizer.setVisible(true);
        });
    }
}
