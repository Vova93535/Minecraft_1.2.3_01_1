import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.stb.*;
import org.lwjgl.system.*;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Minecraft123_01_1 {

    // ======================== Constants ========================
    private static final int WIDTH = 1024;
    private static final int HEIGHT = 768;
    private static final int WORLD_WIDTH = 128;
    private static final int WORLD_HEIGHT = 64;
    private static final int WORLD_DEPTH = 128;
    private static final double MOUSE_SENSITIVITY = 0.002;
    private static final double MOVE_SPEED = 0.2;       // acceleration per tick
    private static final double SLOW_POWER = 0.7;        // friction factor
    private static final double JUMP_FORCE = 0.3;        // upward velocity for jump
    private static final double GRAVITY = 0.01;           // downward acceleration per tick
    private static final double REACH = 5.0;
    private static final int INVENTORY_SIZE = 9;

    // Block types
    private static final int AIR = 0;
    private static final int DIRT = 1;
    private static final int GRASS = 2;
    private static final int STONE = 3;
    private static final int WOOD = 4;
    private static final int LEAVES = 5;
    private static final int CREEPY_EYE = 6;

    private static final float[][] BLOCK_COLORS = {
            null,
            {0.545f, 0.271f, 0.075f, 1.0f},
            {0.133f, 0.545f, 0.133f, 1.0f},
            {0.5f, 0.5f, 0.5f, 1.0f},
            {0.4f, 0.26f, 0.13f, 1.0f},
            {0.0f, 0.5f, 0.0f, 1.0f},
            {1.0f, 0.0f, 0.0f, 0.5f}
    };

    // ======================== World and Player ========================
    private static int[][][] world;
    private static double playerX, playerY, playerZ;
    private static double yaw = 0, pitch = 0;
    private static double velX = 0, velY = 0, velZ = 0; // velocity
    private static boolean[] keys = new boolean[GLFW_KEY_LAST];
    private static boolean leftMousePressed = false;
    private static boolean rightMousePressed = false;
    private static Random rand = new Random(12345);
    private static List<File> soundFiles = new CopyOnWriteArrayList<>();
    private static long lastSoundTime = 0;
    private static final long SOUND_INTERVAL = 15000;

    // Window handle
    private static long window;

    // ======================== New Features ========================
    // Inventory
    private static int[] inventory = new int[INVENTORY_SIZE];
    private static int selectedSlot = 0;

    // Game state
    private static boolean isPaused = false;
    private static boolean optionsMenu = false;
    private static boolean invertMouse = false;
    private static double mouseSensitivity = MOUSE_SENSITIVITY;

    // Chat
    private static List<String> chatMessages = new ArrayList<>();
    private static boolean chatOpen = false;
    private static StringBuilder chatInput = new StringBuilder();
    private static long lastChatEventTime = 0;
    private static final long CHAT_EVENT_INTERVAL = 10000;

    // Crash screen
    private static boolean crashScreenActive = false;
    private static String crashCode = "";
    private static String crashMessage = "";

    // Special entities
    private static class SpecialEntity {
        double x, y, z;
        int type;
        boolean active;
        SpecialEntity(double x, double y, double z, int type) {
            this.x = x; this.y = y; this.z = z; this.type = type; active = true;
        }
    }
    private static List<SpecialEntity> specialEntities = new CopyOnWriteArrayList<>();

    // ======================== Font rendering ========================
    private static STBTTPackedchar.Buffer charData;
    private static int fontTexture;
    private static final int FONT_HEIGHT = 24;

    public static void main(String[] args) {
        loadSoundFiles();
        generateWorld();
        spawnPlayer();
        initInventory();
        run();
    }

    private static void initInventory() {
        Arrays.fill(inventory, DIRT);
        inventory[0] = GRASS;
        inventory[1] = STONE;
        inventory[2] = WOOD;
        inventory[3] = LEAVES;
    }

    private static void loadSoundFiles() {
        String[] paths = {
                "C:\\Windows\\Media",
                "C:\\Windows\\Media\\Windows 7",
                "C:\\Windows\\Media\\Windows 8",
                "C:\\Windows\\Media\\Windows 10",
                "C:\\Windows\\Media\\Windows 11"
        };
        for (String path : paths) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".wav"));
                if (files != null) soundFiles.addAll(Arrays.asList(files));
            }
        }
        if (soundFiles.isEmpty()) System.err.println("No Windows sound files found.");
    }

    private static void playRandomSound() {
        if (soundFiles.isEmpty()) return;
        File f = soundFiles.get(rand.nextInt(soundFiles.size()));
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(f);
            Clip clip = AudioSystem.getClip();
            clip.addLineListener(event -> { if (event.getType() == LineEvent.Type.STOP) clip.close(); });
            clip.open(ais);
            clip.start();
        } catch (Exception ignored) {}
    }

    private static void generateWorld() {
        world = new int[WORLD_HEIGHT][WORLD_WIDTH][WORLD_DEPTH];
        for (int y = 0; y < WORLD_HEIGHT; y++)
            for (int x = 0; x < WORLD_WIDTH; x++)
                for (int z = 0; z < WORLD_DEPTH; z++)
                    world[y][x][z] = AIR;

        double[][] heightMap = new double[WORLD_WIDTH][WORLD_DEPTH];
        for (int x = 0; x < WORLD_WIDTH; x++) {
            for (int z = 0; z < WORLD_DEPTH; z++) {
                double h = Math.sin(x * 0.05) * Math.cos(z * 0.05) * 10
                        + Math.sin(x * 0.1) * 5
                        + Math.cos(z * 0.1) * 5
                        + rand.nextDouble() * 4 - 2;
                heightMap[x][z] = h + WORLD_HEIGHT / 2;
            }
        }

        for (int x = 0; x < WORLD_WIDTH; x++) {
            for (int z = 0; z < WORLD_DEPTH; z++) {
                int groundY = (int) heightMap[x][z];
                groundY = Math.max(0, Math.min(WORLD_HEIGHT - 1, groundY));
                for (int y = 0; y <= groundY; y++) {
                    if (y == groundY) world[y][x][z] = GRASS;
                    else if (y > groundY - 3) world[y][x][z] = DIRT;
                    else world[y][x][z] = STONE;
                }
            }
        }

        for (int t = 0; t < 30; t++) {
            int tx = rand.nextInt(WORLD_WIDTH - 5) + 2;
            int tz = rand.nextInt(WORLD_DEPTH - 5) + 2;
            int groundY = 0;
            for (int y = WORLD_HEIGHT - 1; y >= 0; y--) {
                if (world[y][tx][tz] != AIR) { groundY = y; break; }
            }
            if (groundY > 2 && groundY < WORLD_HEIGHT - 8) {
                for (int h = 1; h <= 4; h++) {
                    if (groundY + h < WORLD_HEIGHT) world[groundY + h][tx][tz] = WOOD;
                }
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = 1; dy <= 3; dy++) {
                            int lx = tx + dx, lz = tz + dz, ly = groundY + 4 + dy;
                            if (lx >= 0 && lx < WORLD_WIDTH && lz >= 0 && lz < WORLD_DEPTH && ly < WORLD_HEIGHT) {
                                if (world[ly][lx][lz] == AIR) world[ly][lx][lz] = LEAVES;
                            }
                        }
                    }
                }
            }
        }

        for (int i = 0; i < 100; i++) {
            int x = rand.nextInt(WORLD_WIDTH), z = rand.nextInt(WORLD_DEPTH), y = rand.nextInt(WORLD_HEIGHT - 10) + 5;
            if (world[y][x][z] == STONE || world[y][x][z] == DIRT) world[y][x][z] = CREEPY_EYE;
        }
    }

    private static void spawnPlayer() {
        int spawnX = WORLD_WIDTH / 2, spawnZ = WORLD_DEPTH / 2, groundY = 0;
        for (int y = WORLD_HEIGHT - 1; y >= 0; y--) {
            if (world[y][spawnX][spawnZ] != AIR) { groundY = y; break; }
        }
        playerX = spawnX + 0.5;
        playerY = groundY + 1.5;
        playerZ = spawnZ + 0.5;
        velX = velY = velZ = 0;
        yaw = 0;
        pitch = 0;
    }

    private static void run() {
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        window = glfwCreateWindow(WIDTH, HEIGHT, "Minecraft 1.2.3_01.1 (Creepy 3D)", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                if (crashScreenActive) {
                    glfwSetWindowShouldClose(window, true);
                } else {
                    isPaused = !isPaused;
                    if (!isPaused) optionsMenu = false;
                }
            }
            if (key >= 0 && key < keys.length) {
                keys[key] = action != GLFW_RELEASE;
            }

            if (chatOpen) {
                if (key == GLFW_KEY_ENTER && action == GLFW_RELEASE) {
                    String msg = chatInput.toString().trim();
                    if (!msg.isEmpty()) {
                        chatMessages.add("[You] " + msg);
                    }
                    chatInput.setLength(0);
                    chatOpen = false;
                } else if (key == GLFW_KEY_BACKSPACE && action != GLFW_RELEASE) {
                    if (chatInput.length() > 0) chatInput.deleteCharAt(chatInput.length() - 1);
                }
            } else {
                if (key == GLFW_KEY_T && action == GLFW_RELEASE) {
                    chatOpen = true;
                }
                if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9 && action == GLFW_RELEASE) {
                    selectedSlot = key - GLFW_KEY_1;
                }
            }
        });

        glfwSetCharCallback(window, (window, codepoint) -> {
            if (chatOpen) chatInput.append((char) codepoint);
        });

        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (!isPaused && !chatOpen && !crashScreenActive) {
                if (button == GLFW_MOUSE_BUTTON_1) leftMousePressed = action == GLFW_PRESS;
                if (button == GLFW_MOUSE_BUTTON_2) rightMousePressed = action == GLFW_PRESS;
            }
        });

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos(window, (vidmode.width() - WIDTH) / 2, (vidmode.height() - HEIGHT) / 2);
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        glClearColor(0.5f, 0.7f, 1.0f, 1.0f);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        initFont();

        long lastTime = System.nanoTime();
        double delta = 0;
        double nsPerTick = 1_000_000_000.0 / 60.0;

        while (!glfwWindowShouldClose(window)) {
            long now = System.nanoTime();
            delta += (now - lastTime) / nsPerTick;
            lastTime = now;

            while (delta >= 1.0) {
                update();
                delta--;
            }

            render();

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSoundTime > SOUND_INTERVAL) {
                lastSoundTime = currentTime;
                playRandomSound();
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private static void update() {
        if (crashScreenActive) return;
        if (isPaused) return;

        // Mouse look
        double[] mouseX = new double[1], mouseY = new double[1];
        glfwGetCursorPos(window, mouseX, mouseY);
        int centerX = WIDTH / 2, centerY = HEIGHT / 2;
        double dx = mouseX[0] - centerX;
        double dy = mouseY[0] - centerY;
        yaw += dx * mouseSensitivity;
        pitch += (invertMouse ? 1 : -1) * dy * mouseSensitivity;
        if (pitch > Math.PI / 2.5) pitch = Math.PI / 2.5;
        if (pitch < -Math.PI / 2.5) pitch = -Math.PI / 2.5;
        glfwSetCursorPos(window, centerX, centerY);

        // Apply acceleration from keys (WASD)
        double accelX = 0, accelZ = 0;
        if (keys[GLFW_KEY_W]) { accelX += Math.sin(yaw); accelZ += Math.cos(yaw); }
        if (keys[GLFW_KEY_S]) { accelX -= Math.sin(yaw); accelZ -= Math.cos(yaw); }
        if (keys[GLFW_KEY_A]) { accelX += Math.sin(yaw - Math.PI/2); accelZ += Math.cos(yaw - Math.PI/2); }
        if (keys[GLFW_KEY_D]) { accelX += Math.sin(yaw + Math.PI/2); accelZ += Math.cos(yaw + Math.PI/2); }
        double len = Math.sqrt(accelX*accelX + accelZ*accelZ);
        if (len > 0) {
            accelX = accelX/len * MOVE_SPEED;
            accelZ = accelZ/len * MOVE_SPEED;
            velX += accelX;
            velZ += accelZ;
        }

        // Jump
        if (keys[GLFW_KEY_SPACE]) {
            // Check if on ground
            int feetY = (int) Math.floor(playerY - 0.1);
            if (feetY >= 0 && feetY < WORLD_HEIGHT) {
                if (world[feetY][(int)playerX][(int)playerZ] != AIR) {
                    velY = JUMP_FORCE;
                }
            }
            keys[GLFW_KEY_SPACE] = false; // prevent repeated jumps
        }

        // Gravity
        velY -= GRAVITY;

        // Apply friction
        velX *= SLOW_POWER;
        velY *= SLOW_POWER;
        velZ *= SLOW_POWER;

        // Tentative new position
        double newX = playerX + velX;
        double newY = playerY + velY;
        double newZ = playerZ + velZ;

        // Simple collision detection (block-level)
        int blockX = (int)Math.floor(newX);
        int blockY = (int)Math.floor(newY);
        int blockZ = (int)Math.floor(newZ);
        int feetBlock = (int)Math.floor(playerY - 0.1);

        // Check if new position is inside a solid block (or would be)
        // We'll just check the block the player's feet/head would occupy
        boolean canMoveX = true, canMoveY = true, canMoveZ = true;
        // For simplicity, we only check the block at the new position (assuming player is 1.8 blocks tall)
        // This is very basic; a real game would check bounding boxes.
        if (blockY >= 0 && blockY < WORLD_HEIGHT && blockX >= 0 && blockX < WORLD_WIDTH && blockZ >= 0 && blockZ < WORLD_DEPTH) {
            if (world[blockY][blockX][blockZ] != AIR || world[blockY+1][blockX][blockZ] != AIR) {
                // Collision, stop movement
                velX = 0;
                velZ = 0;
                velY = 0;
                // But we still want to keep player from falling through floor
                if (velY < 0 && world[feetBlock][(int)playerX][(int)playerZ] != AIR) {
                    playerY = Math.floor(playerY) + 1.5; // snap to ground
                }
                return;
            }
        }

        // If no collision, update position
        playerX = newX;
        playerY = newY;
        playerZ = newZ;

        // Block breaking/placing
        if (leftMousePressed) { breakBlock(); leftMousePressed = false; }
        if (rightMousePressed) { placeBlock(); rightMousePressed = false; }

        // Chat events
        long now = System.currentTimeMillis();
        if (now - lastChatEventTime > CHAT_EVENT_INTERVAL) {
            lastChatEventTime = now;
            if (rand.nextInt(100) < 30) {
                int r = rand.nextInt(100);
                if (r < 10) triggerNullEvent();
                else if (r < 40) triggerHerobrineEvent();
                else if (r < 70) triggerFriendEvent();
                else if (r < 71) triggerNoneEvent();
            }
        }

        // Special entities update
        for (SpecialEntity e : specialEntities) {
            if (!e.active) continue;
            double dist = Math.sqrt((e.x-playerX)*(e.x-playerX) + (e.y-playerY)*(e.y-playerY) + (e.z-playerZ)*(e.z-playerZ));
            if (e.type == 0 && dist < 8.0) {
                showCrashScreen("0x00000001", "not found texture \"Null\"");
            }
            if (e.type == 1 && dist < 3.0) {
                chatMessages.add("[SYSTEM] You feel burning! Herobrine vanishes.");
                e.active = false;
            }
        }
    }

    private static void triggerNullEvent() {
        chatMessages.add("[EVENT] Null appears...");
        double x = playerX + rand.nextInt(20) - 10;
        double z = playerZ + rand.nextInt(20) - 10;
        double y = playerY;
        specialEntities.add(new SpecialEntity(x, y, z, 0));
    }

    private static void triggerHerobrineEvent() {
        chatMessages.add("[EVENT] Herobrine is watching you...");
        double x = playerX + rand.nextInt(20) - 10;
        double z = playerZ + rand.nextInt(20) - 10;
        double y = playerY;
        specialEntities.add(new SpecialEntity(x, y, z, 1));
    }

    private static void triggerFriendEvent() {
        String[] platforms = {"TG", "Steam", "Discord", "Epic"};
        String platform = platforms[rand.nextInt(platforms.length)];
        chatMessages.add("[EVENT] Friend from " + platform + " joined the game!");
        double x = playerX + rand.nextInt(20) - 10;
        double z = playerZ + rand.nextInt(20) - 10;
        double y = playerY;
        specialEntities.add(new SpecialEntity(x, y, z, 2));
    }

    private static void triggerNoneEvent() {
        chatMessages.add("[EVENT] None...");
        if (rand.nextInt(100) < 50) {
            showCrashScreen("none", "not found info");
        }
    }

    private static void breakBlock() {
        double step = 0.1;
        double x = playerX, y = playerY, z = playerZ;
        double dx = Math.sin(yaw) * step, dz = Math.cos(yaw) * step, dy = Math.sin(pitch) * step;
        for (int i = 0; i < REACH / step; i++) {
            int ix = (int)Math.floor(x), iy = (int)Math.floor(y), iz = (int)Math.floor(z);
            if (ix>=0 && ix<WORLD_WIDTH && iy>=0 && iy<WORLD_HEIGHT && iz>=0 && iz<WORLD_DEPTH) {
                if (world[iy][ix][iz] != AIR) {
                    world[iy][ix][iz] = AIR;
                    playRandomSound();
                    return;
                }
            }
            x += dx; y += dy; z += dz;
        }
    }

    private static void placeBlock() {
        int blockToPlace = inventory[selectedSlot];
        double step = 0.1;
        double x = playerX, y = playerY, z = playerZ;
        double dx = Math.sin(yaw) * step, dz = Math.cos(yaw) * step, dy = Math.sin(pitch) * step;
        double prevX = x, prevY = y, prevZ = z;
        for (int i = 0; i < REACH / step; i++) {
            int ix = (int)Math.floor(x), iy = (int)Math.floor(y), iz = (int)Math.floor(z);
            if (ix>=0 && ix<WORLD_WIDTH && iy>=0 && iy<WORLD_HEIGHT && iz>=0 && iz<WORLD_DEPTH) {
                if (world[iy][ix][iz] != AIR) {
                    int px = (int)Math.floor(prevX), py = (int)Math.floor(prevY), pz = (int)Math.floor(prevZ);
                    if (px>=0 && px<WORLD_WIDTH && py>=0 && py<WORLD_HEIGHT && pz>=0 && pz<WORLD_DEPTH) {
                        if (world[py][px][pz] == AIR) {
                            world[py][px][pz] = blockToPlace;
                            playRandomSound();
                        }
                    }
                    return;
                }
            }
            prevX = x; prevY = y; prevZ = z;
            x += dx; y += dy; z += dz;
        }
    }

    private static void showCrashScreen(String code, String message) {
        crashScreenActive = true;
        crashCode = code;
        crashMessage = message;
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }

    private static void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        if (!crashScreenActive) {
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            gluPerspective(70.0f, (float) WIDTH / HEIGHT, 0.1f, 200.0f);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();

            gluLookAt(playerX, playerY, playerZ,
                    playerX + Math.sin(yaw) * Math.cos(pitch),
                    playerY + Math.sin(pitch),
                    playerZ + Math.cos(yaw) * Math.cos(pitch),
                    0, 1, 0);

            int px = (int) playerX, py = (int) playerY, pz = (int) playerZ;
            int viewDist = 32;
            for (int y = py - viewDist; y <= py + viewDist; y++) {
                if (y < 0 || y >= WORLD_HEIGHT) continue;
                for (int x = px - viewDist; x <= px + viewDist; x++) {
                    if (x < 0 || x >= WORLD_WIDTH) continue;
                    for (int z = pz - viewDist; z <= pz + viewDist; z++) {
                        if (z < 0 || z >= WORLD_DEPTH) continue;
                        int block = world[y][x][z];
                        if (block == AIR) continue;
                        double dx = x - playerX, dy = y - playerY, dz = z - playerZ;
                        if (dx*dx + dy*dy + dz*dz > viewDist*viewDist) continue;
                        drawBlock(x, y, z, block);
                    }
                }
            }

            for (SpecialEntity e : specialEntities) {
                if (!e.active) continue;
                glPushMatrix();
                glTranslated(e.x, e.y, e.z);
                float[] col;
                if (e.type == 0) col = new float[]{0,0,0,1};
                else if (e.type == 1) col = new float[]{1,1,1,1};
                else col = new float[]{0,1,0,1};
                glColor4fv(col);
                double s = 0.5;
                glBegin(GL_QUADS);
                glVertex3d(-s, -s, -s); glVertex3d( s, -s, -s); glVertex3d( s,  s, -s); glVertex3d(-s,  s, -s);
                glVertex3d(-s, -s,  s); glVertex3d( s, -s,  s); glVertex3d( s,  s,  s); glVertex3d(-s,  s,  s);
                glVertex3d(-s, -s, -s); glVertex3d(-s,  s, -s); glVertex3d(-s,  s,  s); glVertex3d(-s, -s,  s);
                glVertex3d( s, -s, -s); glVertex3d( s,  s, -s); glVertex3d( s,  s,  s); glVertex3d( s, -s,  s);
                glVertex3d(-s, -s, -s); glVertex3d( s, -s, -s); glVertex3d( s, -s,  s); glVertex3d(-s, -s,  s);
                glVertex3d(-s,  s, -s); glVertex3d( s,  s, -s); glVertex3d( s,  s,  s); glVertex3d(-s,  s,  s);
                glEnd();
                glPopMatrix();
            }
        }

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, WIDTH, 0, HEIGHT, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);

        if (crashScreenActive) {
            drawCrashScreen();
        } else if (isPaused) {
            drawPauseMenu();
        } else {
            drawHUD();
        }

        glEnable(GL_DEPTH_TEST);
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }

    private static void drawHUD() {
        glColor4f(1,1,1,1);
        glBegin(GL_LINES);
        glVertex2i(WIDTH/2 - 10, HEIGHT/2); glVertex2i(WIDTH/2 + 10, HEIGHT/2);
        glVertex2i(WIDTH/2, HEIGHT/2 - 10); glVertex2i(WIDTH/2, HEIGHT/2 + 10);
        glEnd();

        int slotW = 40, slotH = 40, startX = (WIDTH - INVENTORY_SIZE*slotW)/2, y = 20;
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            int x = startX + i*slotW;
            glColor4f(0.5f,0.5f,0.5f,0.8f);
            glBegin(GL_QUADS);
            glVertex2i(x, y); glVertex2i(x+slotW, y); glVertex2i(x+slotW, y+slotH); glVertex2i(x, y+slotH);
            glEnd();
            glColor4f(1,1,1,1);
            glBegin(GL_LINE_LOOP);
            glVertex2i(x, y); glVertex2i(x+slotW, y); glVertex2i(x+slotW, y+slotH); glVertex2i(x, y+slotH);
            glEnd();
            if (i == selectedSlot) {
                glColor4f(1,1,0,1);
                glBegin(GL_LINE_LOOP);
                glVertex2i(x-2, y-2); glVertex2i(x+slotW+2, y-2); glVertex2i(x+slotW+2, y+slotH+2); glVertex2i(x-2, y+slotH+2);
                glEnd();
            }
            float[] col = BLOCK_COLORS[inventory[i]];
            if (col != null) {
                glColor4fv(col);
                glBegin(GL_QUADS);
                glVertex2i(x+5, y+5); glVertex2i(x+slotW-5, y+5); glVertex2i(x+slotW-5, y+slotH-5); glVertex2i(x+5, y+slotH-5);
                glEnd();
            }
        }

        int msgY = HEIGHT - 50;
        glColor4f(0,0,0,0.5f);
        glBegin(GL_QUADS);
        glVertex2i(10, msgY-20); glVertex2i(400, msgY-20); glVertex2i(400, msgY+20*chatMessages.size()); glVertex2i(10, msgY+20*chatMessages.size());
        glEnd();
        glColor4f(1,1,1,1);
        for (int i = 0; i < chatMessages.size(); i++) {
            if (i > 5) break;
            String msg = chatMessages.get(chatMessages.size()-1-i);
            drawText(msg, 15, msgY + i*20);
        }
        if (chatOpen) {
            drawText("> " + chatInput.toString() + "_", 15, msgY + 20*chatMessages.size());
        }
    }

    private static void drawPauseMenu() {
        glColor4f(0,0,0,0.8f);
        glBegin(GL_QUADS);
        glVertex2i(0,0); glVertex2i(WIDTH,0); glVertex2i(WIDTH,HEIGHT); glVertex2i(0,HEIGHT);
        glEnd();
        glColor4f(1,1,1,1);
        drawText("PAUSED", WIDTH/2-50, HEIGHT/2+50);
        drawText("[R] Resume", WIDTH/2-50, HEIGHT/2);
        drawText("[O] Options", WIDTH/2-50, HEIGHT/2-30);
        drawText("[Q] Quit", WIDTH/2-50, HEIGHT/2-60);

        if (keys[GLFW_KEY_R]) isPaused = false;
        if (keys[GLFW_KEY_O]) optionsMenu = true;
        if (keys[GLFW_KEY_Q]) glfwSetWindowShouldClose(window, true);

        if (optionsMenu) {
            glColor4f(0,0,0,0.9f);
            glBegin(GL_QUADS);
            glVertex2i(200,200); glVertex2i(WIDTH-200,200); glVertex2i(WIDTH-200,HEIGHT-200); glVertex2i(200,HEIGHT-200);
            glEnd();
            glColor4f(1,1,1,1);
            drawText("OPTIONS", WIDTH/2-50, HEIGHT-250);
            drawText("Invert Mouse: " + (invertMouse ? "ON" : "OFF") + " (I)", WIDTH/2-100, HEIGHT-300);
            drawText("Sensitivity: " + String.format("%.3f", mouseSensitivity) + " ([/])", WIDTH/2-100, HEIGHT-350);
            drawText("Back (B)", WIDTH/2-100, HEIGHT-400);
            if (keys[GLFW_KEY_I]) { invertMouse = !invertMouse; keys[GLFW_KEY_I] = false; }
            if (keys[GLFW_KEY_LEFT_BRACKET]) { mouseSensitivity -= 0.0005; if (mouseSensitivity < 0.0005) mouseSensitivity = 0.0005; keys[GLFW_KEY_LEFT_BRACKET] = false; }
            if (keys[GLFW_KEY_RIGHT_BRACKET]) { mouseSensitivity += 0.0005; if (mouseSensitivity > 0.01) mouseSensitivity = 0.01; keys[GLFW_KEY_RIGHT_BRACKET] = false; }
            if (keys[GLFW_KEY_B]) { optionsMenu = false; keys[GLFW_KEY_B] = false; }
        }
    }

    private static void drawCrashScreen() {
        glColor4f(1,0,0,1);
        drawText("##### # ###  # ###   ###  # ###", 200, HEIGHT-200);
        drawText("#     ##   # ##   # #   # ##   #", 200, HEIGHT-220);
        drawText("##### #      #      #   # #", 200, HEIGHT-240);
        drawText("#     #      #      #   # #    Connected non-local player", 200, HEIGHT-260);
        drawText("##### #      #       ###  #    " + crashCode, 200, HEIGHT-280);
        drawText("Send: mastervova38@gmail.com", 200, HEIGHT-320);
        drawText(crashMessage, 200, HEIGHT-360);

        glColor4f(0.5f,0.5f,0.5f,1);
        glBegin(GL_QUADS);
        glVertex2i(WIDTH/2-50, 100); glVertex2i(WIDTH/2+50, 100); glVertex2i(WIDTH/2+50, 150); glVertex2i(WIDTH/2-50, 150);
        glEnd();
        glColor4f(1,1,1,1);
        drawText("OK", WIDTH/2-10, 125);
    }

    // ======================== Font Rendering Implementation ========================

    private static void initFont() {
        String[] fontPaths = {
                "C:\\Windows\\Fonts\\consola.ttf",
                "C:\\Windows\\Fonts\\cour.ttf",
                "C:\\Windows\\Fonts\\lucon.ttf"
        };
        ByteBuffer fontBuffer = null;
        for (String path : fontPaths) {
            try {
                fontBuffer = createByteBufferFromFile(path);
                if (fontBuffer != null) break;
            } catch (Exception ignored) {}
        }
        if (fontBuffer == null) {
            System.err.println("Could not load any font. Text will be invisible.");
            return;
        }

        int bitmapW = 1024, bitmapH = 1024;
        ByteBuffer bitmap = BufferUtils.createByteBuffer(bitmapW * bitmapH);
        STBTTPackContext pc = STBTTPackContext.malloc();
        stbtt_PackBegin(pc, bitmap, bitmapW, bitmapH, 0, 1, NULL);
        stbtt_PackSetOversampling(pc, 2, 2);

        int firstChar = 0x20;
        int numChars = 0x52F - 0x20 + 1;
        charData = STBTTPackedchar.malloc(numChars);
        stbtt_PackFontRange(pc, fontBuffer, 0, FONT_HEIGHT, firstChar, charData);

        stbtt_PackEnd(pc);
        pc.free();

        fontTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, bitmapW, bitmapH, 0, GL_ALPHA, GL_UNSIGNED_BYTE, bitmap);
    }

    private static void drawText(String text, int x, int y) {
        if (charData == null) return;
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1, 1, 1, 1);
        glBegin(GL_QUADS);
        float xpos = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c > 0x52F) c = 0x20;
            int index = c - 0x20;
            STBTTPackedchar ch = charData.get(index);
            float x0 = xpos + ch.xoff();
            float y0 = y + ch.yoff();
            float x1 = x0 + (ch.xoff2() - ch.xoff());
            float y1 = y + ch.yoff2();
            float s0 = ch.x0() / 1024.0f;
            float s1 = ch.x1() / 1024.0f;
	        float t0 = ch.y0() / 1024.0f;
	        float t1 = ch.y1() / 1024.0f;

            glTexCoord2f(s0, t0); glVertex2f(x0, y1);
            glTexCoord2f(s1, t0); glVertex2f(x1, y1);
            glTexCoord2f(s1, t1); glVertex2f(x1, y0);
            glTexCoord2f(s0, t1); glVertex2f(x0, y0);

            xpos += ch.xadvance();
        }
        glEnd();
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
    }

    private static ByteBuffer createByteBufferFromFile(String path) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(path, "r")) {
            byte[] data = new byte[(int) f.length()];
            f.readFully(data);
            ByteBuffer buffer = BufferUtils.createByteBuffer(data.length);
            buffer.put(data);
            buffer.flip();
            return buffer;
        }
    }

    private static void drawBlock(int x, int y, int z, int type) {
        float[] c = BLOCK_COLORS[type];
        if (c == null) return;
        glColor4fv(c);
        glBegin(GL_QUADS);
        glVertex3d(x, y, z+1); glVertex3d(x+1, y, z+1); glVertex3d(x+1, y+1, z+1); glVertex3d(x, y+1, z+1);
        glVertex3d(x, y, z); glVertex3d(x, y+1, z); glVertex3d(x+1, y+1, z); glVertex3d(x+1, y, z);
        glVertex3d(x+1, y, z); glVertex3d(x+1, y+1, z); glVertex3d(x+1, y+1, z+1); glVertex3d(x+1, y, z+1);
        glVertex3d(x, y, z); glVertex3d(x, y, z+1); glVertex3d(x, y+1, z+1); glVertex3d(x, y+1, z);
        glVertex3d(x, y+1, z); glVertex3d(x, y+1, z+1); glVertex3d(x+1, y+1, z+1); glVertex3d(x+1, y+1, z);
        glVertex3d(x, y, z); glVertex3d(x+1, y, z); glVertex3d(x+1, y, z+1); glVertex3d(x, y, z+1);
        glEnd();
    }

    private static void gluPerspective(float fov, float aspect, float near, float far) {
        float fh = (float) Math.tan(fov / 360 * Math.PI) * near;
        float fw = fh * aspect;
        glFrustum(-fw, fw, -fh, fh, near, far);
    }

    private static void gluLookAt(double eyeX, double eyeY, double eyeZ,
                                  double centerX, double centerY, double centerZ,
                                  double upX, double upY, double upZ) {
        double fX = centerX - eyeX, fY = centerY - eyeY, fZ = centerZ - eyeZ;
        double fLen = Math.sqrt(fX*fX + fY*fY + fZ*fZ);
        fX /= fLen; fY /= fLen; fZ /= fLen;

        double sX = fY * upZ - fZ * upY;
        double sY = fZ * upX - fX * upZ;
        double sZ = fX * upY - fY * upX;
        double sLen = Math.sqrt(sX*sX + sY*sY + sZ*sZ);
        sX /= sLen; sY /= sLen; sZ /= sLen;

        double uX = sY * fZ - sZ * fY;
        double uY = sZ * fX - sX * fZ;
        double uZ = sX * fY - sY * fX;

        float[] m = new float[16];
        m[0] = (float) sX; m[4] = (float) sY; m[8] = (float) sZ; m[12] = 0;
        m[1] = (float) uX; m[5] = (float) uY; m[9] = (float) uZ; m[13] = 0;
        m[2] = (float)-fX; m[6] = (float)-fY; m[10]= (float)-fZ; m[14] = 0;
        m[3] = 0; m[7] = 0; m[11] = 0; m[15] = 1;

        glMultMatrixf(m);
        glTranslated(-eyeX, -eyeY, -eyeZ);
    }
}