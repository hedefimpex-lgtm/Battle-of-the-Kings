package com.battleofthekings.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/** A small, asset-free arcade brawler. All gameplay coordinates are in a 960 x 540 world. */
public final class GameView extends View {
    private static final float WIDTH = 960f;
    private static final float HEIGHT = 540f;
    private static final float END_X = 1900f;
    private static final float TOP_LANE = 288f;
    private static final float BOTTOM_LANE = 440f;
    private static final float[] WAVE_START = {320f, 840f, 1360f};
    private static final String[] REALMS = {"KÜL KIYISI", "BUZ GEÇİDİ", "KRAL SALONU"};
    private static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;
    private static final int STRIKE = 4, CLEAVE = 5, DODGE = 6;

    private enum Mode { TITLE, PLAYING, PAUSED, GAME_OVER, VICTORY }
    private enum EnemyKind { RAIDER, GUARD, BRUTE, CHAMPION }

    private static final class Enemy {
        final EnemyKind kind;
        final float maxHealth;
        float x, y, health, cooldown, stun, flash;
        int facing = -1;

        Enemy(EnemyKind kind, float x, float y, float health) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.health = health;
            this.maxHealth = health;
            this.cooldown = 0.5f;
        }
    }

    private static final class Spark {
        float x, y, vx, vy, life;
        final int color;

        Spark(float x, float y, float vx, float vy, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = 0.4f;
            this.color = color;
        }
    }

    private static final class Rune {
        float x, y;
        final boolean healing;

        Rune(float x, float y, boolean healing) {
            this.x = x;
            this.y = y;
            this.healing = healing;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final ArrayList<Enemy> enemies = new ArrayList<>();
    private final ArrayList<Spark> sparks = new ArrayList<>();
    private final ArrayList<Rune> runes = new ArrayList<>();
    private final Map<Integer, Integer> pointers = new HashMap<>();
    private final boolean[] movement = new boolean[4];
    private Mode mode = Mode.TITLE;
    private long lastFrame;
    private int realm, wave, score;
    private boolean waveActive;
    private float heroX = 100f, heroY = 370f, health = 100f, fury;
    private float cameraX, attackTimer, attackCooldown, dodgeTimer, dodgeCooldown;
    private float invincible, screenShake, titlePulse, messageTimer;
    private int facing = 1, attackStyle;
    private String message = "";

    public GameView(Context context) {
        super(context);
        setFocusable(true);
    }

    public void pauseFromActivity() {
        if (mode == Mode.PLAYING) mode = Mode.PAUSED;
        clearPointers();
    }

    public boolean handleBack() {
        if (mode == Mode.PLAYING) {
            mode = Mode.PAUSED;
            clearPointers();
            return true;
        }
        if (mode == Mode.PAUSED) {
            mode = Mode.PLAYING;
            return true;
        }
        return false;
    }

    private void newGame() {
        realm = 0;
        score = 0;
        health = 100f;
        fury = 0f;
        startRealm();
        mode = Mode.PLAYING;
    }

    private void startRealm() {
        wave = 0;
        waveActive = false;
        heroX = 100f;
        heroY = 370f;
        facing = 1;
        cameraX = 0f;
        attackTimer = attackCooldown = dodgeTimer = dodgeCooldown = invincible = 0f;
        enemies.clear();
        sparks.clear();
        runes.clear();
        clearPointers();
        message = REALMS[realm];
        messageTimer = 2.2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.033f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        titlePulse += dt;
        if (mode == Mode.PLAYING) update(dt);

        float scale = Math.min(getWidth() / WIDTH, getHeight() / HEIGHT);
        canvas.save();
        canvas.translate((getWidth() - WIDTH * scale) / 2f, (getHeight() - HEIGHT * scale) / 2f);
        canvas.scale(scale, scale);
        drawScene(canvas);
        canvas.restore();
        postInvalidateOnAnimation();
    }

    private void update(float dt) {
        attackTimer = Math.max(0f, attackTimer - dt);
        attackCooldown = Math.max(0f, attackCooldown - dt);
        dodgeTimer = Math.max(0f, dodgeTimer - dt);
        dodgeCooldown = Math.max(0f, dodgeCooldown - dt);
        invincible = Math.max(0f, invincible - dt);
        screenShake = Math.max(0f, screenShake - dt);
        messageTimer = Math.max(0f, messageTimer - dt);

        float dx = (movement[RIGHT] ? 1f : 0f) - (movement[LEFT] ? 1f : 0f);
        float dy = (movement[DOWN] ? 1f : 0f) - (movement[UP] ? 1f : 0f);
        if (dx != 0f) facing = dx > 0f ? 1 : -1;
        if (dodgeTimer > 0f) {
            heroX += facing * 525f * dt;
        } else {
            float diagonal = dx != 0f && dy != 0f ? 0.7071f : 1f;
            heroX += dx * diagonal * 235f * dt;
            heroY += dy * diagonal * 175f * dt;
        }
        heroY = clamp(heroY, TOP_LANE, BOTTOM_LANE);
        float gate = waveActive ? WAVE_START[wave] + 385f : END_X;
        heroX = clamp(heroX, 45f, gate);
        cameraX = clamp(heroX - 300f, 0f, END_X - WIDTH);

        if (!waveActive && wave < 3 && heroX >= WAVE_START[wave]) spawnWave();
        updateEnemies(dt);
        if (mode != Mode.PLAYING) return;
        updateSparks(dt);
        updateRunes();

        if (waveActive && enemies.isEmpty()) {
            waveActive = false;
            wave++;
            message = wave == 3 ? "YOL AÇILDI" : "İLERLE";
            messageTimer = 1.5f;
        }
        if (wave == 3 && heroX >= END_X - 50f) {
            if (realm == REALMS.length - 1) {
                mode = Mode.VICTORY;
            } else {
                realm++;
                health = Math.min(100f, health + 30f);
                startRealm();
            }
        }
    }

    private void spawnWave() {
        waveActive = true;
        float x = heroX + 220f;
        if (wave == 0) {
            addEnemy(EnemyKind.RAIDER, x, 335f);
            addEnemy(realm == 0 ? EnemyKind.RAIDER : EnemyKind.GUARD, x + 90f, 405f);
            if (realm > 0) addEnemy(EnemyKind.RAIDER, x + 165f, 365f);
        } else if (wave == 1) {
            addEnemy(EnemyKind.GUARD, x, 335f);
            addEnemy(EnemyKind.RAIDER, x + 80f, 405f);
            addEnemy(EnemyKind.BRUTE, x + 160f, 365f);
        } else {
            addEnemy(EnemyKind.RAIDER, x, 320f);
            addEnemy(EnemyKind.GUARD, x + 90f, 425f);
            addEnemy(EnemyKind.CHAMPION, x + 225f, 370f);
            message = "ŞAMPİYON GELDİ";
            messageTimer = 2f;
        }
    }

    private void addEnemy(EnemyKind kind, float x, float y) {
        float hp;
        switch (kind) {
            case GUARD: hp = 48f; break;
            case BRUTE: hp = 85f; break;
            case CHAMPION: hp = 160f + realm * 30f; break;
            default: hp = 35f;
        }
        enemies.add(new Enemy(kind, x, y, hp + realm * 8f));
    }

    private void updateEnemies(float dt) {
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy e = it.next();
            if (e.health <= 0f) {
                score += e.kind == EnemyKind.CHAMPION ? 500 : 100;
                if (e.kind == EnemyKind.CHAMPION || random.nextFloat() < 0.28f) {
                    runes.add(new Rune(e.x, e.y, health < 55f || random.nextBoolean()));
                }
                burst(e.x, e.y - 32f, 12, 0xffe2b771);
                it.remove();
                continue;
            }
            e.cooldown = Math.max(0f, e.cooldown - dt);
            e.stun = Math.max(0f, e.stun - dt);
            e.flash = Math.max(0f, e.flash - dt);
            if (e.stun > 0f) continue;
            float xDistance = heroX - e.x;
            float yDistance = heroY - e.y;
            e.facing = xDistance >= 0f ? 1 : -1;
            float reach = e.kind == EnemyKind.CHAMPION ? 82f : 58f;
            if (Math.abs(xDistance) > reach || Math.abs(yDistance) > 37f) {
                float speed = e.kind == EnemyKind.BRUTE || e.kind == EnemyKind.CHAMPION ? 82f : 106f;
                e.x += Math.signum(xDistance) * speed * dt;
                e.y += Math.signum(yDistance) * Math.min(Math.abs(yDistance), speed * 0.6f * dt);
                e.y = clamp(e.y, TOP_LANE, BOTTOM_LANE);
            } else if (e.cooldown == 0f) {
                e.cooldown = e.kind == EnemyKind.CHAMPION ? 1.25f : 1.0f;
                if (dodgeTimer == 0f && invincible == 0f) {
                    health = Math.max(0f, health - (e.kind == EnemyKind.CHAMPION ? 17f : 9f));
                    invincible = 0.6f;
                    screenShake = 0.18f;
                    burst(heroX, heroY - 35f, 7, 0xffff6e58);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (health <= 0f) {
                        mode = Mode.GAME_OVER;
                        clearPointers();
                    }
                }
            }
        }
    }

    private void updateSparks(float dt) {
        Iterator<Spark> it = sparks.iterator();
        while (it.hasNext()) {
            Spark s = it.next();
            s.life -= dt;
            if (s.life <= 0f) {
                it.remove();
            } else {
                s.x += s.vx * dt;
                s.y += s.vy * dt;
                s.vy += 260f * dt;
            }
        }
    }

    private void updateRunes() {
        Iterator<Rune> it = runes.iterator();
        while (it.hasNext()) {
            Rune rune = it.next();
            if (Math.abs(rune.x - heroX) < 35f && Math.abs(rune.y - heroY) < 34f) {
                if (rune.healing) health = Math.min(100f, health + 25f);
                else fury = Math.min(100f, fury + 30f);
                burst(rune.x, rune.y - 20f, 8, rune.healing ? 0xff78e6a5 : 0xff84cfff);
                it.remove();
            }
        }
    }

    private void attack(boolean heavy) {
        if (mode != Mode.PLAYING || attackCooldown > 0f || dodgeTimer > 0f) return;
        boolean powered = heavy && fury >= 50f;
        if (powered) fury -= 50f;
        attackStyle = heavy ? 2 : 1;
        attackTimer = heavy ? 0.33f : 0.23f;
        attackCooldown = heavy ? 0.68f : 0.34f;
        float range = heavy ? 145f : 106f;
        float damage = heavy ? (powered ? 55f : 34f) : 21f;
        boolean landed = false;
        for (Enemy e : enemies) {
            float forward = (e.x - heroX) * facing;
            if (forward > -16f && forward < range && Math.abs(e.y - heroY) < (heavy ? 62f : 43f)) {
                e.health -= damage;
                e.stun = heavy ? 0.42f : 0.22f;
                e.flash = 0.16f;
                e.x = clamp(e.x + facing * (heavy ? 48f : 25f), 30f, END_X - 25f);
                fury = Math.min(100f, fury + (powered ? 0f : 8f));
                burst(e.x, e.y - 38f, heavy ? 12 : 7, powered ? 0xff96dfff : 0xffffcb77);
                landed = true;
            }
        }
        if (landed) {
            screenShake = heavy ? 0.16f : 0.07f;
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    private void dodge() {
        if (mode == Mode.PLAYING && dodgeCooldown == 0f && dodgeTimer == 0f) {
            dodgeTimer = 0.27f;
            dodgeCooldown = 0.95f;
            burst(heroX, heroY - 20f, 7, 0xffa1d9ec);
        }
    }

    private void burst(float x, float y, int count, int color) {
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float) Math.PI * 2f;
            float speed = 65f + random.nextFloat() * 150f;
            sparks.add(new Spark(x, y, (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed - 50f, color));
        }
    }

    private static float clamp(float n, float min, float max) {
        return Math.max(min, Math.min(max, n));
    }

    private void drawScene(Canvas canvas) {
        canvas.save();
        if (screenShake > 0f) canvas.translate((random.nextFloat() - 0.5f) * 8f, (random.nextFloat() - 0.5f) * 5f);
        drawLandscape(canvas);
        drawFighters(canvas);
        drawPickupsAndSparks(canvas);
        canvas.restore();

        if (mode == Mode.PLAYING) {
            drawHud(canvas);
            drawControls(canvas);
            if (messageTimer > 0f) {
                text(canvas, message, WIDTH / 2f, 100f, 31f, 0xffffe4aa, true);
            }
        } else {
            drawOverlay(canvas);
        }
    }

    private void drawLandscape(Canvas c) {
        int[] skyTop = {0xff233747, 0xff1b354b, 0xff302432};
        int[] skyBottom = {0xffb16f50, 0xff8bafc3, 0xff945841};
        paint.setShader(new LinearGradient(0, 0, 0, 315, skyTop[realm], skyBottom[realm], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, WIDTH, 320f, paint);
        paint.setShader(null);

        fill(0x55ffe1aa);
        c.drawCircle(realm == 1 ? 760f : 180f, realm == 1 ? 95f : 115f, realm == 1 ? 36f : 48f, paint);
        fill(realm == 1 ? 0xff41647b : 0xff473f48);
        for (int i = -2; i < 8; i++) {
            float base = i * 180f - (cameraX * 0.18f % 180f);
            Path mountain = new Path();
            mountain.moveTo(base - 130f, 302f);
            mountain.lineTo(base + 15f, 150f + (i % 2) * 38f);
            mountain.lineTo(base + 165f, 302f);
            mountain.close();
            c.drawPath(mountain, paint);
        }
        fill(realm == 1 ? 0xff7390a0 : 0xff584b4a);
        for (int i = -1; i < 9; i++) {
            float base = i * 150f - (cameraX * 0.35f % 150f);
            Path hill = new Path();
            hill.moveTo(base - 100f, 313f);
            hill.lineTo(base + 35f, 225f + (i % 3) * 20f);
            hill.lineTo(base + 180f, 313f);
            hill.close();
            c.drawPath(hill, paint);
        }

        fill(realm == 1 ? 0xffb5c8cc : realm == 2 ? 0xff765145 : 0xff5e594d);
        c.drawRect(0, 284f, WIDTH, HEIGHT, paint);
        fill(realm == 1 ? 0xffe1e8df : realm == 2 ? 0xffa97857 : 0xff817a62);
        c.drawRect(0, 284f, WIDTH, 311f, paint);
        fill(0x44702017);
        for (int x = (int) (cameraX / 120f) * 120; x < cameraX + WIDTH + 140f; x += 120) {
            float sx = x - cameraX;
            c.drawLine(sx, 310f, sx - 65f, HEIGHT, paint);
            c.drawLine(sx - 120f, 375f, sx + 120f, 375f, paint);
            c.drawLine(sx - 120f, 470f, sx + 120f, 470f, paint);
        }

        for (int x = 130; x < END_X; x += 350) {
            float sx = x - cameraX;
            if (sx < -80f || sx > WIDTH + 80f) continue;
            fill(realm == 1 ? 0xffd6e2dc : 0xff6a5b50);
            c.drawRect(sx - 18f, 207f, sx + 18f, 289f, paint);
            c.drawRect(sx - 28f, 204f, sx + 28f, 216f, paint);
            fill(0x6650332a);
            c.drawRect(sx - 25f, 285f, sx + 25f, 298f, paint);
        }
        if (wave == 3) {
            float sx = END_X - 65f - cameraX;
            fill(0xff2d2529);
            c.drawRoundRect(new RectF(sx - 42f, 205f, sx + 42f, 312f), 13f, 13f, paint);
            fill(0xffd4a55c);
            c.drawRoundRect(new RectF(sx - 29f, 218f, sx + 29f, 310f), 7f, 7f, paint);
            fill(0xff403842);
            c.drawRect(sx - 23f, 230f, sx + 23f, 310f, paint);
        }
    }

    private void drawFighters(Canvas c) {
        Collections.sort(enemies, new Comparator<Enemy>() {
            @Override public int compare(Enemy a, Enemy b) { return Float.compare(a.y, b.y); }
        });
        boolean heroDrawn = false;
        for (Enemy e : enemies) {
            if (!heroDrawn && e.y >= heroY) {
                drawHero(c);
                heroDrawn = true;
            }
            drawEnemy(c, e);
        }
        if (!heroDrawn) drawHero(c);
    }

    private void drawHero(Canvas c) {
        float x = heroX - cameraX;
        fill(0x660b1621);
        c.drawOval(new RectF(x - 27f, heroY - 8f, x + 29f, heroY + 10f), paint);
        if (invincible > 0f && ((int) (titlePulse * 16f) & 1) == 0) return;
        c.save();
        c.translate(x, heroY);
        c.scale(facing, 1f);
        if (dodgeTimer > 0f) {
            fill(0x6679d8e5);
            c.drawOval(new RectF(-36f, -80f, 24f, -3f), paint);
        }
        fill(0xff42333a);
        c.drawRoundRect(new RectF(-19f, -32f, -3f, 0f), 5f, 5f, paint);
        c.drawRoundRect(new RectF(4f, -32f, 22f, 0f), 5f, 5f, paint);
        fill(0xff9b3f35);
        Path cloak = new Path();
        cloak.moveTo(-12f, -78f);
        cloak.lineTo(-33f, -20f);
        cloak.lineTo(1f, -24f);
        cloak.close();
        c.drawPath(cloak, paint);
        fill(0xffc2a176);
        c.drawRoundRect(new RectF(-18f, -82f, 20f, -30f), 8f, 8f, paint);
        fill(0xff4f5559);
        c.drawRoundRect(new RectF(-18f, -77f, 20f, -45f), 7f, 7f, paint);
        fill(0xffe0bf8d);
        c.drawCircle(0f, -94f, 17f, paint);
        fill(0xff493931);
        c.drawArc(new RectF(-17f, -112f, 17f, -76f), 183f, 175f, true, paint);
        c.drawRoundRect(new RectF(-7f, -91f, 10f, -77f), 6f, 6f, paint);
        fill(0xfff2e4c0);
        c.drawCircle(7f, -97f, 2f, paint);
        fill(0xffa48061);
        c.drawRoundRect(new RectF(13f, -72f, 28f, -42f), 7f, 7f, paint);
        fill(0xff523d31);
        c.drawLine(25f, -54f, 47f, -42f, paint);
        paint.setStrokeWidth(7f);
        c.drawLine(30f, -64f, 63f, -27f, paint);
        fill(0xffc4d5d8);
        Path axe = new Path();
        axe.moveTo(57f, -47f);
        axe.lineTo(75f, -58f);
        axe.lineTo(83f, -34f);
        axe.lineTo(64f, -22f);
        axe.close();
        c.drawPath(axe, paint);
        if (attackTimer > 0f) {
            paint.setColor(attackStyle == 2 ? 0xff93deef : 0xffffdc8f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(attackStyle == 2 ? 13f : 8f);
            c.drawArc(new RectF(-27f, -127f, attackStyle == 2 ? 150f : 112f, 12f), -68f, 114f, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        c.restore();
    }

    private void drawEnemy(Canvas c, Enemy e) {
        float x = e.x - cameraX;
        if (x < -100f || x > WIDTH + 100f) return;
        float size = e.kind == EnemyKind.CHAMPION ? 1.42f : e.kind == EnemyKind.BRUTE ? 1.18f : 1f;
        fill(0x660b1621);
        c.drawOval(new RectF(x - 27f * size, e.y - 8f, x + 27f * size, e.y + 9f), paint);
        c.save();
        c.translate(x, e.y);
        c.scale(e.facing * size, size);
        int armor = e.kind == EnemyKind.CHAMPION ? 0xff654045
                : e.kind == EnemyKind.GUARD ? 0xff536c73
                : e.kind == EnemyKind.BRUTE ? 0xff6b5941 : 0xff685c4c;
        fill(e.flash > 0f ? Color.WHITE : 0xff292b32);
        c.drawRoundRect(new RectF(-19f, -30f, -3f, 0f), 5f, 5f, paint);
        c.drawRoundRect(new RectF(4f, -30f, 20f, 0f), 5f, 5f, paint);
        fill(e.flash > 0f ? Color.WHITE : armor);
        c.drawRoundRect(new RectF(-22f, -80f, 22f, -30f), 9f, 9f, paint);
        fill(e.flash > 0f ? Color.WHITE : 0xffa88d71);
        c.drawCircle(0f, -91f, 16f, paint);
        fill(e.flash > 0f ? Color.WHITE : 0xff292d37);
        c.drawArc(new RectF(-19f, -110f, 19f, -75f), 180f, 180f, true, paint);
        fill(0xffff756a);
        c.drawCircle(8f, -94f, 2f, paint);
        if (e.kind == EnemyKind.GUARD) {
            fill(0xffa9b7b4);
            c.drawOval(new RectF(-32f, -75f, -8f, -39f), paint);
            fill(0xff536c73);
            c.drawCircle(-20f, -57f, 7f, paint);
        }
        if (e.kind == EnemyKind.CHAMPION) {
            fill(0xffd2b26e);
            Path crown = new Path();
            crown.moveTo(-18f, -106f);
            crown.lineTo(-17f, -124f);
            crown.lineTo(-6f, -112f);
            crown.lineTo(0f, -129f);
            crown.lineTo(7f, -112f);
            crown.lineTo(17f, -124f);
            crown.lineTo(18f, -106f);
            crown.close();
            c.drawPath(crown, paint);
        }
        fill(0xff493c35);
        paint.setStrokeWidth(6f);
        c.drawLine(19f, -63f, 39f, -30f, paint);
        fill(e.kind == EnemyKind.CHAMPION ? 0xffffd287 : 0xffc0c8c4);
        c.drawRect(33f, -49f, 47f, -25f, paint);
        c.restore();
        if (e.kind == EnemyKind.CHAMPION || e.health < e.maxHealth) {
            fill(0xaa241f24);
            c.drawRoundRect(new RectF(x - 32f, e.y - 132f * size, x + 32f, e.y - 126f * size), 3f, 3f, paint);
            fill(0xffec7665);
            c.drawRoundRect(new RectF(x - 32f, e.y - 132f * size,
                    x - 32f + 64f * Math.max(0f, e.health) / e.maxHealth, e.y - 126f * size), 3f, 3f, paint);
        }
    }

    private void drawPickupsAndSparks(Canvas c) {
        for (Rune rune : runes) {
            float x = rune.x - cameraX;
            int color = rune.healing ? 0xff7ee3a2 : 0xff83cfff;
            fill(0x55444444);
            c.drawOval(new RectF(x - 17f, rune.y - 3f, x + 17f, rune.y + 7f), paint);
            fill(color);
            Path gem = new Path();
            gem.moveTo(x, rune.y - 38f);
            gem.lineTo(x + 13f, rune.y - 25f);
            gem.lineTo(x, rune.y - 11f);
            gem.lineTo(x - 13f, rune.y - 25f);
            gem.close();
            c.drawPath(gem, paint);
        }
        for (Spark spark : sparks) {
            fill(spark.color);
            c.drawCircle(spark.x - cameraX, spark.y, Math.max(1f, spark.life * 9f), paint);
        }
    }

    private void drawHud(Canvas c) {
        fill(0xbb12191e);
        c.drawRoundRect(new RectF(18f, 17f, 284f, 89f), 14f, 14f, paint);
        text(c, "CAN", 34f, 43f, 17f, Color.WHITE, false);
        fill(0xff543a3d);
        c.drawRoundRect(new RectF(94f, 29f, 264f, 43f), 7f, 7f, paint);
        fill(0xffef7264);
        c.drawRoundRect(new RectF(94f, 29f, 94f + 170f * health / 100f, 43f), 7f, 7f, paint);
        text(c, "ÖFKE", 34f, 72f, 16f, Color.WHITE, false);
        fill(0xff314d5c);
        c.drawRoundRect(new RectF(94f, 58f, 264f, 72f), 7f, 7f, paint);
        fill(0xff80cfed);
        c.drawRoundRect(new RectF(94f, 58f, 94f + 170f * fury / 100f, 72f), 7f, 7f, paint);
        text(c, REALMS[realm] + "  •  " + (wave == 3 ? "ÇIKIŞ" : (wave + 1) + "/3"), WIDTH / 2f, 39f, 18f, Color.WHITE, true);
        text(c, "★ " + score, WIDTH / 2f, 64f, 17f, 0xffffd78d, true);
        drawButton(c, 921f, 40f, 28f, "Ⅱ", false, 17f);
    }

    private void drawControls(Canvas c) {
        drawButton(c, 85f, 439f, 37f, "◀", movement[LEFT], 24f);
        drawButton(c, 205f, 439f, 37f, "▶", movement[RIGHT], 24f);
        drawButton(c, 145f, 377f, 37f, "▲", movement[UP], 23f);
        drawButton(c, 145f, 500f, 37f, "▼", movement[DOWN], 23f);
        drawButton(c, 754f, 452f, 45f, "VUR", false, 18f);
        drawButton(c, 846f, 390f, 43f, "YAR", false, 18f);
        drawButton(c, 895f, 490f, 43f, "KAÇ", false, 18f);
        if (dodgeCooldown > 0f) {
            text(c, String.format(java.util.Locale.ROOT, "%.1f", dodgeCooldown), 895f, 441f, 14f, Color.WHITE, true);
        }
    }

    private void drawButton(Canvas c, float x, float y, float radius, String label, boolean active, float size) {
        fill(active ? 0xccdfad65 : 0x99303c48);
        c.drawCircle(x, y, radius, paint);
        paint.setColor(0xaaddddc8);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        c.drawCircle(x, y, radius - 2f, paint);
        paint.setStyle(Paint.Style.FILL);
        text(c, label, x, y + size * 0.34f, size, Color.WHITE, true);
    }

    private void drawOverlay(Canvas c) {
        fill(0xc9131c24);
        c.drawRect(0f, 0f, WIDTH, HEIGHT, paint);
        String title;
        String subtitle;
        if (mode == Mode.TITLE) {
            title = "KRALLARIN SAVAŞI";
            subtitle = "Üç diyar. Dokuz pusu. Son bir kral.";
        } else if (mode == Mode.PAUSED) {
            title = "DURAKLATILDI";
            subtitle = "Devam etmek için ekrana dokun";
        } else if (mode == Mode.GAME_OVER) {
            title = "YOLUN SONU";
            subtitle = "Yeniden denemek için ekrana dokun";
        } else {
            title = "ZAFER";
            subtitle = "Kralın salonu fethedildi • Puan: " + score;
        }
        text(c, title, WIDTH / 2f, 212f, mode == Mode.TITLE ? 50f : 48f, 0xffffdc9e, true);
        text(c, subtitle, WIDTH / 2f, 260f, 22f, Color.WHITE, true);
        if (mode == Mode.TITLE) {
            text(c, "Yön tuşlarıyla ilerle • VUR / YAR ile saldır • KAÇ ile sıyrıl", WIDTH / 2f, 307f, 17f, 0xffd6dde1, true);
        }
        if (mode != Mode.PAUSED) {
            fill(0xffd8a85c);
            c.drawRoundRect(new RectF(350f, 345f, 610f, 403f), 16f, 16f, paint);
            text(c, mode == Mode.TITLE ? "BAŞLA" : "TEKRAR OYNA", WIDTH / 2f, 383f, 24f, 0xff1a222a, true);
        }
    }

    private void fill(int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int color, boolean center) {
        fill(color);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(size);
        paint.setTextAlign(center ? Paint.Align.CENTER : Paint.Align.LEFT);
        c.drawText(value, x, baseline, paint);
    }

    private void clearPointers() {
        pointers.clear();
        for (int i = 0; i < movement.length; i++) movement[i] = false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float scale = Math.min(getWidth() / WIDTH, getHeight() / HEIGHT);
        if (scale <= 0f) return true;
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float x = (event.getX(index) - (getWidth() - WIDTH * scale) / 2f) / scale;
            float y = (event.getY(index) - (getHeight() - HEIGHT * scale) / 2f) / scale;
            press(event.getPointerId(index), x, y);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            pointers.remove(event.getPointerId(index));
            refreshMovement();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            clearPointers();
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int pointer = event.getPointerId(i);
                Integer current = pointers.get(pointer);
                if (current != null && current < 4) {
                    float x = (event.getX(i) - (getWidth() - WIDTH * scale) / 2f) / scale;
                    float y = (event.getY(i) - (getHeight() - HEIGHT * scale) / 2f) / scale;
                    int next = buttonAt(x, y);
                    if (next >= 0 && next < 4) pointers.put(pointer, next);
                }
            }
            refreshMovement();
        }
        return true;
    }

    private void press(int pointer, float x, float y) {
        if (mode == Mode.TITLE || mode == Mode.GAME_OVER || mode == Mode.VICTORY) {
            newGame();
            return;
        }
        if (mode == Mode.PAUSED) {
            mode = Mode.PLAYING;
            return;
        }
        if (x > 890f && y < 78f) {
            mode = Mode.PAUSED;
            clearPointers();
            return;
        }
        int button = buttonAt(x, y);
        if (button < 0) return;
        if (button < 4) {
            pointers.put(pointer, button);
            refreshMovement();
        } else if (button == STRIKE) attack(false);
        else if (button == CLEAVE) attack(true);
        else if (button == DODGE) dodge();
    }

    private void refreshMovement() {
        for (int i = 0; i < movement.length; i++) movement[i] = false;
        for (int button : pointers.values()) if (button < 4) movement[button] = true;
    }

    private int buttonAt(float x, float y) {
        if (near(x, y, 85f, 439f, 44f)) return LEFT;
        if (near(x, y, 205f, 439f, 44f)) return RIGHT;
        if (near(x, y, 145f, 377f, 44f)) return UP;
        if (near(x, y, 145f, 500f, 44f)) return DOWN;
        if (near(x, y, 754f, 452f, 51f)) return STRIKE;
        if (near(x, y, 846f, 390f, 50f)) return CLEAVE;
        if (near(x, y, 895f, 490f, 51f)) return DODGE;
        return -1;
    }

    private static boolean near(float x, float y, float cx, float cy, float radius) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }
}
