package records;

import engine.*;
import entity.*;
import screen.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Exercises real collision handlers against the current course gameplay. */
public final class GameplayIntegrationTest {
    private static int checks;
    private static Object get(Object target, String name) throws Exception {
        Field f = field(target.getClass(), name); return f.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        while (type != null) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException e) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static void call(Object target, String name) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(target);
    }
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory(Paths.get("out"), "gameplay-integration-");
        Field global = Core.class.getDeclaredField("recordsService"); global.setAccessible(true);
        Object previous = global.get(null);
        RecordsService service = new RecordsService(directory.resolve("profile.properties"));
        global.set(null, service);
        try {
            for (String path : Arrays.asList("bullet", "beam", "contact")) campaign(service, path);
            infinite(service);
            menusAndResults();
            timingAndAbort(service);
            service.startRun("abandoned"); service.abandonRun("abandoned");
            check(!service.enemyDefeated("abandoned", "late"), "abandoned run rejects late events");
            check(service.getRecordCount() == 1, "only natural death creates a completed record");
        } finally { global.set(null, previous); service.close(); }
        System.out.println("GameplayIntegrationTest passed: " + checks + " checks.");
    }
    private static boolean[] keys() throws Exception {
        Field f = InputManager.class.getDeclaredField("keys"); f.setAccessible(true);
        return (boolean[])f.get(null);
    }
    private static void menusAndResults() throws Exception {
        TitleScreen title = new TitleScreen(448,520,60);
        set(title,"inputDelay",Core.getCooldown(0)); set(title,"selectionCooldown",Core.getCooldown(0));
        keys()[java.awt.event.KeyEvent.VK_I]=true;
        try { call(title,"update"); }
        finally { Arrays.fill(keys(),false); }
        check((Integer)get(title,"returnCode")==8,"I shortcut retains upstream infinite route 8");
        for (boolean infinite : new boolean[]{false,true}) {
            ScoreScreen score = new ScoreScreen(448,520,60,new GameState(1,100,0,1,1),
                    infinite,Arrays.asList(Achievement.FIRST_ENEMY));
            set(score,"inputDelay",Core.getCooldown(0)); set(score,"isNewRecord",false);
            keys()[java.awt.event.KeyEvent.VK_SPACE]=true;
            try { call(score,"update"); }
            finally { Arrays.fill(keys(),false); }
            check((Integer)get(score,"returnCode")== (infinite ? 8 : 2),"results replay preserves mode");
            check(!(Boolean)get(score,"gameCleared"),"failed or infinite run is not campaign cleared");
            javax.imageio.ImageIO.write((java.awt.image.BufferedImage)get(Core.getDrawManager(),"backBuffer"),
                    "png",Paths.get("out",infinite ? "infinite-results.png" : "campaign-results.png").toFile());
        }
        ScoreScreen cleared = new ScoreScreen(448,520,60,
                new GameState(campaign.CampaignStageData.getTotalStages()+1,500,3,0,20),
                false,Arrays.asList(Achievement.FIRST_RUN,Achievement.FIRST_ENEMY,Achievement.TWENTY_ENEMIES,
                        Achievement.SIXTY_SECONDS,Achievement.BEAT_HIGH_SCORE));
        call(cleared,"draw");
        check((Boolean)get(cleared,"gameCleared"),"campaign victory retained with achievement list");
        javax.imageio.ImageIO.write((java.awt.image.BufferedImage)get(Core.getDrawManager(),"backBuffer"),
                "png",Paths.get("out","campaign-cleared-results.png").toFile());
    }
    private static void timingAndAbort(RecordsService service) throws Exception {
        service.startRun("timing"); RunSession session=new RunSession("timing");
        InfiniteGameScreen game=new InfiniteGameScreen(new GameState(1,0,3,0,0),448,520,60,session);
        game.initialize(); call(game,"update");
        check(session.getActiveDurationMillis(System.nanoTime())==0,"infinite input delay excluded");
        set(game,"inputDelay",Core.getCooldown(0));
        session.startActiveSegment(System.nanoTime()-60001000000L);
        call(game,"update");
        check(service.getUnlockTime(Achievement.SIXTY_SECONDS)!=null,"infinite active timer unlocks sixty seconds");
        keys()[java.awt.event.KeyEvent.VK_ESCAPE]=true;
        try { call(game,"update"); }
        finally { Arrays.fill(keys(),false); }
        long stopped=session.getActiveDurationMillis(System.nanoTime());
        check(game.isAbandoned(),"Escape marks infinite run abandoned");
        check(session.getActiveDurationMillis(System.nanoTime()+5000000000L)==stopped,"Escape stops active timing");
        check(!service.enemyDefeated("timing","late"),"Escape invalidates active achievement events");
        service.startRun("death"); RunSession deadSession=new RunSession("death");
        InfiniteGameScreen dead=new InfiniteGameScreen(new GameState(1,100,0,2,1),448,520,60,deadSession);
        dead.initialize(); set(dead,"inputDelay",Core.getCooldown(0)); call(dead,"update");
        long duration=deadSession.getActiveDurationMillis(System.nanoTime());
        check(!dead.isAbandoned() && (Boolean)get(dead,"gameOver"),"natural death is eligible for completion");
        check(deadSession.getActiveDurationMillis(System.nanoTime()+5000000000L)==duration,"end animation excluded");
        RunRecord record=deadSession.finish(100,1,System.currentTimeMillis());
        check(service.completeRun(record),"natural infinite death creates completed record");
    }
    @SuppressWarnings("unchecked")
    private static void campaign(RecordsService service, String path) throws Exception {
        service.startRun(path);
        GameScreen game = new GameScreen(new GameState(7,0,3,0,0),
                new GameSettings(1,1,1000,10000),false,448,520,60,
                // Heavy Tank: the shield-absorption check below needs a ship
                // that can hold the Energy Shield.
                ShipType.TANK,new RunSession(path));
        game.initialize(); set(game,"inputDelay",Core.getCooldown(0));
        Ship player = (Ship)get(game,"ship");
        EnemyShipFormation formation = (EnemyShipFormation)get(game,"enemyShipFormation");
        EnemyShip normal = formation.iterator().next();
        normal.setPositionX(player.getPositionX()); normal.setPositionY(150);
        EnemyShip special = new EnemyShip(); special.setPositionX(player.getPositionX()); special.setPositionY(80);
        set(get(game,"bonusEnemyManager"),"bonusShip",special);
        final List<String> events = new ArrayList<String>();
        game.getGameplayEvents().subscribe(event.EnemyDefeatedEvent.class,
                new event.GameplayEventListener<event.EnemyDefeatedEvent>() {
                    @Override
                    public void onEvent(final event.EnemyDefeatedEvent e) { events.add(e.getEventId()); }
                });
        player.setShieldActive(true);
        Set<Bullet> incoming = (Set<Bullet>)get(game,"bullets");
        incoming.add(BulletPool.getBullet(player.getPositionX()+4,player.getPositionY()+4,4));
        call(game,"manageCollisions");
        check(events.isEmpty() && game.getGameState().getLivesRemaining()==3,
                path+" shield absorption is not an enemy defeat");
        player.setShieldActive(false);
        if (path.equals("bullet")) {
            Set<Bullet> bullets = (Set<Bullet>)get(game,"bullets");
            bullets.add(BulletPool.getBullet(normal.getPositionX()+4,normal.getPositionY()+4,-4));
            bullets.add(BulletPool.getBullet(special.getPositionX()+4,special.getPositionY()+4,-4));
            call(game,"manageCollisions"); call(game,"manageCollisions");
        } else if (path.equals("beam")) {
            call(game,"manageAnnihilatorBeam"); call(game,"manageAnnihilatorBeam");
        } else {
            player.activateChameleonHull();
            normal.setPositionX(player.getPositionX()); normal.setPositionY(player.getPositionY());
            special.setPositionX(player.getPositionX()); special.setPositionY(player.getPositionY());
            call(game,"manageBallContact"); call(game,"manageBallContact");
        }
        check(events.size()==2, path+" reports exactly two enemy defeats");
        check(new HashSet<String>(events).size()==2, path+" supplies distinct event IDs");
        check(game.getGameState().getShipsDestroyed()==2, path+" preserves upstream kill totals");
        set(game,"levelFinished",true);
        call(game,"manageAnnihilatorBeam"); call(game,"manageCollisions");
        check(events.size()==2,path+" does not count kills after the stage ends");
    }
    @SuppressWarnings("unchecked")
    private static void infinite(RecordsService service) throws Exception {
        service.startRun("infinite");
        InfiniteGameScreen game = new InfiniteGameScreen(new GameState(1,0,3,0,0),448,520,60,new RunSession("infinite"));
        game.initialize();
        List<ZigzagEnemyShip> enemies = (List<ZigzagEnemyShip>)get(game,"enemies");
        Set<Bullet> bullets = (Set<Bullet>)get(game,"bullets");
        final List<String> events = new ArrayList<String>();
        game.getGameplayEvents().subscribe(event.EnemyDefeatedEvent.class,
                new event.GameplayEventListener<event.EnemyDefeatedEvent>() {
                    @Override
                    public void onEvent(final event.EnemyDefeatedEvent e) { events.add(e.getEventId()); }
                });
        enemies.add(new ZigzagEnemyShip(100,100,DrawManager.SpriteType.EnemyShipA1,1));
        bullets.add(BulletPool.getBullet(104,104,-4));
        call(game,"manageCollisions"); call(game,"manageCollisions");
        check(events.size()==1 && game.getGameState().getShipsDestroyed()==1,"infinite bullet kill is counted once");
        enemies.add(new ZigzagEnemyShip(100,600,DrawManager.SpriteType.EnemyShipA1,1));
        call(game,"moveEnemies");
        check(events.size()==1,"escaped infinite enemy does not count as defeated");
        Ship player=(Ship)get(game,"ship");
        enemies.add(new ZigzagEnemyShip(player.getPositionX(),player.getPositionY(),DrawManager.SpriteType.EnemyShipA1,1));
        call(game,"manageCollisions");
        check(events.size()==1 && game.getGameState().getLivesRemaining()==2,"player contact loses life without awarding a defeat");
        set(game,"gameOver",true);
        enemies.add(new ZigzagEnemyShip(100,100,DrawManager.SpriteType.EnemyShipA1,1));
        bullets.add(BulletPool.getBullet(104,104,-4)); call(game,"manageCollisions");
        check(events.size()==1,"infinite end animation cannot create more defeats");
    }
}
