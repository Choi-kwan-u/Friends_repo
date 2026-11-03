package records;
import java.util.List;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
public final class RecordsSmokeTest {
    private static int checks;
    private interface CheckedAction { void run(); }
    public static void main(final String[] args) throws Exception {
        if (args.length==2 && "--shutdown-probe".equals(args[0])) { runShutdownProbe(Paths.get(args[1])); return; }
        testRunRecordValidation();
        testRepositoryDeduplication();
        testRunSessionTimingAndFinish();
        testFormatterOutput();
        testPersonalBests();
        testRankingOrderAndLimit();
        testCorruptProfileRecovery();
        testProfilePersistence();
        testShutdownHookFlush();
        testAchievementsAndPersistence();
        testWriteFailureAndLifecycle();
        testCompletionThresholdBoundary();
        testLatestMainDefeatAdapter();
        testRecordsPageNavigationAndRender();
        testLatestMainMenuOrder();
        System.out.println("RecordsSmokeTest passed: "+checks+" checks.");
    }
private static void testRunRecordValidation() {
		RunRecord record = new RunRecord("run-a", -100, 4, 2500, 123456);
		assertEquals("run-a", record.getRunId(), "run id");
		assertEquals(-100, record.getScore(), "score can follow game state");
		assertEquals(4, record.getEnemiesDefeated(), "enemies defeated");
		assertEquals(2500L, record.getActiveDurationMillis(),
				"active duration");
		assertEquals(123456L, record.getCompletionTimeMillis(),
				"completion time");

		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunRecord(null, 0, 0, 0, 1);
			}
		}, "null run id");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunRecord("  ", 0, 0, 0, 1);
			}
		}, "blank run id");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunRecord("bad-enemies", 0, -1, 0, 1);
			}
		}, "negative enemies");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunRecord("bad-duration", 0, 0, -1, 1);
			}
		}, "negative duration");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunRecord("bad-time", 0, 0, 0, 0);
			}
		}, "non-positive completion time");
	}

private static void testRepositoryDeduplication() {
		final RunRecordRepository repository =
				new InMemoryRunRecordRepository();
		RunRecord first = new RunRecord("run-a", 100, 3, 2000, 10);
		RunRecord duplicate = new RunRecord("run-a", 999, 9, 9000, 99);
		RunRecord sameScore = new RunRecord("run-b", 100, 3, 2000, 20);

		assertEquals(0, repository.size(), "empty repository");
		assertTrue(repository.add(first), "first record is added");
		assertFalse(repository.add(duplicate), "duplicate run id is ignored");
		assertTrue(repository.add(sameScore),
				"same score with a new run id is added");
		assertEquals(2, repository.size(), "only unique runs are stored");

		final List<RunRecord> snapshot = repository.getAll();
		assertEquals(2, snapshot.size(), "snapshot size");
		assertSame(first, snapshot.get(0), "insertion order is retained");
		expectException(UnsupportedOperationException.class,
				new CheckedAction() {
					public void run() {
						snapshot.clear();
					}
				}, "snapshot cannot be modified");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				repository.add(null);
			}
		}, "null record");
	}

private static void testRunSessionTimingAndFinish() {
		RunSession session = new RunSession("run-timed");
		session.startActiveSegment(1000000000L);
		assertEquals(1000L, session.getActiveDurationMillis(2000000000L),
				"live active segment is visible to achievements");
		session.startActiveSegment(1500000000L);
		session.stopActiveSegment(3000000000L);
		session.stopActiveSegment(4000000000L);
		session.startActiveSegment(5000000000L);
		session.stopActiveSegment(8000000000L);

		RunRecord completed = session.finish(750, 12, 50000);
		assertEquals("run-timed", completed.getRunId(), "session run id");
		assertEquals(5000L, completed.getActiveDurationMillis(),
				"only active segments are accumulated");
		assertEquals(750, completed.getScore(), "session score");
		assertEquals(12, completed.getEnemiesDefeated(),
				"session enemies defeated");

		RunRecord duplicateFinish = session.finish(999, 99, 99999);
		assertSame(completed, duplicateFinish,
				"repeated finish returns the original record");
		assertEquals(750, duplicateFinish.getScore(),
				"repeated finish does not replace values");
		session.startActiveSegment(9000000000L);
		session.stopActiveSegment(12000000000L);
		assertEquals(5000L, session.finish(0, 0, 1)
				.getActiveDurationMillis(), "finished sessions no longer change");

		final RunSession activeSession = new RunSession("run-active");
		activeSession.startActiveSegment(10);
		expectException(IllegalStateException.class, new CheckedAction() {
			public void run() {
				activeSession.finish(0, 0, 1);
			}
		}, "active session must stop before finish");
		activeSession.stopActiveSegment(20);
		assertEquals(0L, activeSession.finish(0, 0, 1)
				.getActiveDurationMillis(), "sub-millisecond time rounds down");

		final RunSession invalidClock = new RunSession("run-clock");
		invalidClock.startActiveSegment(20);
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				invalidClock.stopActiveSegment(19);
			}
		}, "clock cannot move backwards");
		invalidClock.stopActiveSegment(30);

		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				new RunSession("");
			}
		}, "session run id is required");
	}

private static void testFormatterOutput() {
		RunRecord record = new RunRecord("run-output", 800, 15, 12000,
				987654321);
		String output = RunRecordFormatter.format(record);
		assertContains(output, "Run ID: run-output", "formatted run id");
		assertContains(output, "Score: 800", "formatted score");
		assertContains(output, "Enemies defeated: 15",
				"formatted enemies");
		assertContains(output, "Active duration: 12000 ms (12.000 s)",
				"formatted duration");
		assertContains(output, "Completion time: 987654321",
				"formatted completion time");
		expectException(IllegalArgumentException.class, new CheckedAction() {
			public void run() {
				RunRecordFormatter.format(null);
			}
		}, "formatter rejects null");
	}

private static void testPersonalBests() throws Exception {
    RecordsService service = new RecordsService(testDirectory("personal-bests").resolve("profile.properties"));
    assertEquals(0, service.getHighestScore(), "empty score best");
    service.completeRun(new RunRecord("a", -50, 4, 9000, 1));
    service.completeRun(new RunRecord("b", -60, 7, 8000, 2));
    service.completeRun(new RunRecord("c", -50, 7, 9000, 3));
    assertEquals(-50, service.getHighestScore(), "score best and ties");
    assertEquals(7, service.getMostEnemiesDefeated(), "enemy best and ties");
    assertEquals(9000L, service.getLongestSurvivalMillis(), "duration best and ties");
    service.close();
}
private static void testRankingOrderAndLimit() {
		try {
			Path profile = testDirectory("invaders-ranking-test")
					.resolve("profile.properties");
			RecordsService service = new RecordsService(profile);
			service.completeRun(new RunRecord("z", 100, 0, 0, 20));
			service.completeRun(new RunRecord("a", 100, 0, 0, 10));
			service.completeRun(new RunRecord("b", 100, 0, 0, 10));
			for (int i = 0; i < 9; i++)
				service.completeRun(new RunRecord("low-" + i, i, 0, 0, 30 + i));
			List<RunRecord> top = service.getTopRuns();
			assertEquals(10, top.size(), "ranking is capped at ten entries");
			assertEquals("a", top.get(0).getRunId(), "earlier tie sorts first");
			assertEquals("b", top.get(1).getRunId(), "run ID breaks timestamp tie");
			assertEquals("z", top.get(2).getRunId(), "later completion follows");
			service.close();
		} catch (IOException exception) {
			throw new AssertionError("ranking test failed", exception);
		}
	}

private static void testCorruptProfileRecovery() {
		try {
			Path directory = testDirectory("invaders-recovery-test");
			Path profile = directory.resolve("profile.properties");
			ProfileStore store = new ProfileStore(profile);
			List<RunRecord> records = new ArrayList<RunRecord>();
			records.add(new RunRecord("recover-me", 42, 1, 2000, 77));
			ProfileSnapshot first = new ProfileSnapshot(records,
					new LinkedHashMap<String, Long>());
			store.write(first);
			records.add(new RunRecord("newer", 43, 1, 2000, 78));
			store.write(new ProfileSnapshot(records,
					new LinkedHashMap<String, Long>()));
			Files.write(profile, "broken profile".getBytes(StandardCharsets.UTF_8));
			assertEquals(1, store.load().getRecords().size(),
					"valid backup is restored after corruption");
			assertTrue(store.getLoadWarning() != null,
					"backup recovery reports possible lost newer progress");
			try (java.nio.file.DirectoryStream<Path> preserved =
					Files.newDirectoryStream(directory, "profile.properties.corrupt-*")) {
				assertTrue(preserved.iterator().hasNext(),
						"a separate corrupt copy is preserved after recovery");
			}

			Path unrecoverable = directory.resolve("unrecoverable.properties");
			byte[] corrupt = "keep these bytes".getBytes(StandardCharsets.UTF_8);
			Files.write(unrecoverable, corrupt);
			RecordsService blocked = new RecordsService(unrecoverable);
			assertTrue(blocked.isWritesBlocked(),
					"invalid data without a backup blocks automatic writes");
			blocked.completeRun(new RunRecord("ephemeral", 9, 0, 0, 100));
			blocked.close();
			assertTrue(java.util.Arrays.equals(corrupt,
					Files.readAllBytes(unrecoverable)),
					"unrecoverable source bytes remain untouched");
		} catch (IOException exception) {
			throw new AssertionError("profile recovery test failed", exception);
		}
	}

private static void testProfilePersistence() throws Exception {
    Path profile = testDirectory("profile-persistence").resolve("profile.properties");
    RecordsService service = new RecordsService(profile);
    service.completeRun(new RunRecord("persist", 100, 3, 12000, 1));
    service.close(); service.close();
    assertTrue(service.getSaveError() == null, "profile writes successfully");
    RecordsService restored = new RecordsService(profile);
    assertEquals(1, restored.getRecordCount(), "records restore");
    assertEquals(100, restored.getHighestScore(), "best restores from records");
    restored.close();
    Path protectedProfile = testDirectory("protected-primary").resolve("profile.properties");
    new ProfileStore(protectedProfile).write(new ProfileSnapshot(
            java.util.Arrays.asList(new RunRecord("original",100,0,0,1)), new LinkedHashMap<String,Long>()));
    byte[] original = Files.readAllBytes(protectedProfile);
    Path backup = protectedProfile.resolveSibling("profile.properties.bak");
    Files.createDirectory(backup);
    Files.write(backup.resolve("keep"), new byte[] {42});
    RecordsService blocked = new RecordsService(protectedProfile);
    blocked.completeRun(new RunRecord("blocked", 200, 4, 20000, 2));
    blocked.close();
    assertTrue(blocked.getSaveError() != null, "backup failure is reported");
    assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(protectedProfile)), "backup failure preserves primary");
    assertFalse(blocked.completeRun(new RunRecord("late", 0, 0, 0, 3)), "closed service rejects completions");
}
private static void testShutdownHookFlush() throws Exception {
		Path profile = testDirectory("shutdown-probe").resolve("profile.properties")
				.toAbsolutePath();
		String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
		String javaExecutable = Paths.get(System.getProperty("java.home"), "bin", executable).toString();
		List<String> command = new ArrayList<>();
		command.add(javaExecutable);
		String coverageAgent = System.getProperty("invaders.test.coverageAgent");
		if (coverageAgent != null && !coverageAgent.isBlank()) {
			command.add(coverageAgent);
		}
		java.util.Collections.addAll(command, "-Djava.awt.headless=true", "-cp",
				System.getProperty("java.class.path"), RecordsSmokeTest.class.getName(),
				"--shutdown-probe", profile.toString());
		Process child = new ProcessBuilder(command).redirectErrorStream(true).start();
		if (!child.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
			child.destroyForcibly();
			throw new AssertionError("shutdown probe timed out");
		}
		assertEquals(0, child.exitValue(), "shutdown probe exits normally");
		RecordsService restored = new RecordsService(profile);
		try {
			assertEquals(80, restored.getRecordCount(),
					"production shutdown hook drains all pending record writes");
			assertEquals(79, restored.getHighestScore(),
					"the final queued snapshot survives process restart");
		} finally {
			restored.close();
		}
	}

private static void runShutdownProbe(final Path profile) throws Exception {
		RecordsService service = new RecordsService(profile);
		java.lang.reflect.Method register = engine.Core.class.getDeclaredMethod(
				"registerRecordsShutdownHook", RecordsService.class);
		register.setAccessible(true);
		register.invoke(null, service);
		for (int i = 0; i < 80; i++)
			service.completeRun(new RunRecord("shutdown-" + i, i, 0, 0, i + 1));
		// Intentionally omit close(): the production hook must flush this queue.
		System.exit(0);
	}

private static void testAchievementsAndPersistence() {
		try {
			Path directory = testDirectory("invaders-profile-test");
			Path profile = directory.resolve("profile.properties");
			RecordsService service = new RecordsService(profile);
			service.startRun("first");
			assertTrue(service.enemyDefeated("first", "enemy-1"),
					"first defeat is accepted");
			assertFalse(service.enemyDefeated("first", "enemy-1"),
					"duplicate defeat event is ignored");
			for (int i = 2; i < 20; i++)
				service.enemyDefeated("first", "enemy-" + i);
			assertTrue(service.getUnlockTime(Achievement.TWENTY_ENEMIES) == null,
					"nineteen defeats do not unlock the twenty-defeat achievement");
			service.enemyDefeated("first", "enemy-20");
			assertTrue(service.getUnlockTime(Achievement.FIRST_ENEMY) != null,
					"first enemy achievement unlocks");
			assertTrue(service.getUnlockTime(Achievement.TWENTY_ENEMIES) != null,
					"twentieth enemy achievement unlocks");
			service.activeDuration("first", 59999L);
			assertTrue(service.getUnlockTime(Achievement.SIXTY_SECONDS) == null,
					"duration achievement waits for the threshold");
			service.activeDuration("first", 60000L);
			assertTrue(service.getUnlockTime(Achievement.SIXTY_SECONDS) != null,
					"duration achievement unlocks at the threshold");
			assertTrue(service.completeRun(new RunRecord("first", 500, 20,
					60000L, 1000L)), "completed run is recorded");
			assertTrue(service.getUnlockTime(Achievement.FIRST_RUN) != null,
					"first completed run unlocks first-run achievement");
			assertEquals(4, service.getCurrentRunUnlocks().size(),
					"new unlocks are kept for the run summary");
			assertFalse(service.enemyDefeated("first", "late-defeat"),
					"finished runs reject late defeat events");
			assertFalse(service.completeRun(new RunRecord("first", 900, 30,
					90000L, 2000L)), "duplicate completed run is ignored");
			service.startRun("same-score");
			assertTrue(service.pollNotification() != null,
					"unshown notices remain queued when a new run starts");
			service.completeRun(new RunRecord("same-score", 500, 0, 1000,
					3000L));
			assertTrue(service.getUnlockTime(Achievement.BEAT_HIGH_SCORE) == null,
					"equal score does not beat the previous best");
			service.startRun("new-best");
			service.completeRun(new RunRecord("new-best", 501, 0, 1000,
					4000L));
			assertTrue(service.getUnlockTime(Achievement.BEAT_HIGH_SCORE) != null,
					"strictly higher score unlocks the achievement");
			service.close();
			assertTrue(Files.exists(profile), "profile file was written: "
					+ service.getSaveError());

			RecordsService restored = new RecordsService(profile);
			assertEquals(3, restored.getRecordCount(),
					"records restore after restart: " + restored.getSaveError()
					+ " profile=" + profile + " contents="
					+ (Files.exists(profile) ? new String(Files.readAllBytes(profile),
							StandardCharsets.UTF_8) : "<missing>"));
			assertTrue(restored.getUnlockTime(Achievement.BEAT_HIGH_SCORE) != null,
					"achievement unlock restores after restart");
			assertEquals(501, restored.getHighestScore(),
					"personal best restores from records");
			restored.close();

			RecordsService negative = new RecordsService(
					testDirectory("invaders-negative-score").resolve("profile.properties"));
			negative.completeRun(new RunRecord("negative", -50, 0, 0, 101));
			assertEquals(-50, negative.getHighestScore(),
					"personal best preserves a negative recorded score");
			negative.close();
		} catch (IOException exception) {
			throw new AssertionError("profile persistence test failed", exception);
		}
	}

private static void testWriteFailureAndLifecycle() {
		try {
			Path directory = testDirectory("invaders-write-failure");
			Path profile = directory.resolve("profile.properties");
			List<RunRecord> records = new ArrayList<RunRecord>();
			records.add(new RunRecord("minimum", Integer.MIN_VALUE, 0, 0, 1));
			new ProfileStore(profile).write(new ProfileSnapshot(records,
					new LinkedHashMap<String, Long>()));
			RecordsService service = new RecordsService(profile);
			service.startRun("greater");
			service.completeRun(new RunRecord("greater", Integer.MIN_VALUE + 1,
					0, 0, 2));
			assertTrue(service.getUnlockTime(Achievement.BEAT_HIGH_SCORE) != null,
					"minimum integer score remains a valid previous best");
			service.close();
			service.close();
			assertTrue(service.getSaveError() == null, "repeated close flushes safely");
			assertFalse(service.completeRun(new RunRecord("after-close", 0, 0, 0, 3)),
					"closed service rejects new completion events");
			assertFalse(service.enemyDefeated("greater", "after-close"),
					"closed service rejects defeat events");

			Path protectedProfile = directory.resolve("protected.properties");
			new ProfileStore(protectedProfile).write(new ProfileSnapshot(records,
					new LinkedHashMap<String, Long>()));
			byte[] original = Files.readAllBytes(protectedProfile);
			Path blockedBackup = protectedProfile.resolveSibling("protected.properties.bak");
			Files.createDirectory(blockedBackup);
			Files.write(blockedBackup.resolve("keep.txt"), new byte[] { 42 });
			RecordsService failing = new RecordsService(protectedProfile);
			failing.completeRun(new RunRecord("cannot-save", 10, 0, 0, 4));
			failing.close();
			assertTrue(failing.getSaveError() != null,
					"backup replacement failure is reported");
			assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(protectedProfile)),
					"failed backup replacement leaves the valid primary untouched");
			assertTrue(Files.exists(blockedBackup.resolve("keep.txt")),
					"failed backup replacement preserves the existing backup path");
			assertEquals(1, new ProfileStore(protectedProfile).load().getRecords().size(),
					"a failed save leaves a readable previous profile");
		} catch (IOException exception) {
			throw new AssertionError("write failure and lifecycle test failed", exception);
		}
	}

private static void testCompletionThresholdBoundary() {
		try {
			RecordsService service = new RecordsService(testDirectory("completion-boundary")
					.resolve("profile.properties"));
			service.startRun("last-frame");
			service.activeDuration("last-frame", 59999);
			service.completeRun(new RunRecord("last-frame", 50, 20, 60000, 1));
			assertTrue(service.getUnlockTime(Achievement.SIXTY_SECONDS) != null,
					"last-frame duration threshold is checked at completion");
			assertTrue(service.getUnlockTime(Achievement.TWENTY_ENEMIES) != null,
					"completed defeat total provides the final threshold check");
			assertEquals(4, service.getCurrentRunUnlocks().size(),
					"completion unlocks are included once in the summary");
			service.close();
		} catch (IOException exception) {
			throw new AssertionError("completion boundary test failed", exception);
		}
	}

private static void testLatestMainDefeatAdapter() throws Exception {
    RecordsService service = new RecordsService(testDirectory("event-adapter").resolve("profile.properties"));
    java.lang.reflect.Field global = engine.Core.class.getDeclaredField("recordsService");
    global.setAccessible(true); Object previous = global.get(null); global.set(null,service);
    try {
        service.startRun("adapter");
        screen.GameScreen game = new screen.GameScreen(new engine.GameState(1,0,3,0,0),
                new engine.GameSettings(2,2,40,1000),false,432,490,60,new RunSession("adapter"));
        java.lang.reflect.Method defeated = screen.GameScreen.class.getDeclaredMethod("recordEnemyDefeat",entity.EnemyShip.class);
        defeated.setAccessible(true);
        entity.EnemyShip first = new entity.EnemyShip();
        defeated.invoke(game,first); defeated.invoke(game,first);
        for (int i=0;i<18;i++) defeated.invoke(game,new entity.EnemyShip());
        assertTrue(service.getUnlockTime(Achievement.TWENTY_ENEMIES)==null,"duplicate source entity not double-counted");
        defeated.invoke(game,new entity.EnemyShip());
        assertTrue(service.getUnlockTime(Achievement.TWENTY_ENEMIES)!=null,"shared publisher forwards twentieth distinct defeat");
    } finally { global.set(null,previous); service.close(); }
}
private static void testRecordsPageNavigationAndRender() {
		RecordsService service = null;
		try {
			Path directory = Paths.get("out", "records-tests", "render");
			Files.createDirectories(directory);
			service = new RecordsService(testDirectory("render-profile").resolve("profile.properties"));
			service.startRun("render-0");
			for (int i = 0; i < 20; i++)
				service.enemyDefeated("render-0", "enemy-" + i);
			service.activeDuration("render-0", 60000);
			for (int i = 0; i < 10; i++)
				service.completeRun(new RunRecord("render-" + i, 10000 + i * 100,
						20 + i, 60000 + i * 1000, 1791850000000L + i));
			engine.InputManager input = engine.Core.getInputManager();
			java.awt.Canvas source = new java.awt.Canvas();
			for (int key : new int[] { java.awt.event.KeyEvent.VK_ESCAPE,
					java.awt.event.KeyEvent.VK_SPACE }) {
				screen.PlayerRecordsScreen page = new screen.PlayerRecordsScreen(432, 490, 60, service);
				input.keyPressed(new java.awt.event.KeyEvent(source,
						java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, key, '\u0000'));
				try {
					assertEquals(1, page.run(), "records page returns to menu for key " + key);
				} finally {
					input.keyReleased(new java.awt.event.KeyEvent(source,
							java.awt.event.KeyEvent.KEY_RELEASED, 0, 0, key, '\u0000'));
				}
			}
			engine.DrawManager draw = engine.Core.getDrawManager();
			screen.Screen page = new screen.Screen(432, 490, 60);
			java.lang.reflect.Field buffer = engine.DrawManager.class.getDeclaredField("backBuffer");
			buffer.setAccessible(true);
			for (int tab = 0; tab < 2; tab++) {
				draw.initDrawing(page);
				draw.drawRecordsPage(page, service, tab);
				javax.imageio.ImageIO.write((java.awt.image.BufferedImage) buffer.get(null),
						"png", directory.resolve(tab == 0 ? "records.png" : "achievements.png").toFile());
				draw.completeDrawing(page);
			}
			draw.initDrawing(page);
			draw.drawAchievementResults(page, 10000, 0, 20, 0.5f, true,
					new char[] { 'A', 'A', 'A' }, 0,
					java.util.Arrays.asList(Achievement.values()), true, 0);
			javax.imageio.ImageIO.write((java.awt.image.BufferedImage) buffer.get(null),
					"png", directory.resolve("results.png").toFile());
			draw.completeDrawing(page);
			System.out.println("UI review images: " + directory.toAbsolutePath());
		} catch (Exception exception) {
			throw new AssertionError("records page navigation/render test failed", exception);
		} finally {
			if (service != null)
				service.close();
		}
	}

private static void testLatestMainMenuOrder() throws Exception {
    screen.TitleScreen menu = new screen.TitleScreen(432,490,60);
    java.lang.reflect.Field code = screen.Screen.class.getDeclaredField("returnCode"); code.setAccessible(true);
    java.lang.reflect.Method next = screen.TitleScreen.class.getDeclaredMethod("nextMenuItem"); next.setAccessible(true);
    java.lang.reflect.Method previous = screen.TitleScreen.class.getDeclaredMethod("previousMenuItem"); previous.setAccessible(true);
    for (int expected : new int[]{10,11,3,9,4,0,2}) { next.invoke(menu); assertEquals(expected,code.getInt(menu),"forward menu includes Shop, Hangar, Settings, and Records"); }
    for (int expected : new int[]{0,4,9,3,11,10,2}) { previous.invoke(menu); assertEquals(expected,code.getInt(menu),"reverse menu includes Shop, Hangar, Settings, and Records"); }
}
	private static void assertTrue(final boolean actual,
			final String message) {
		checks++;
		if (!actual)
			throw new AssertionError(message);
	}
	private static void assertFalse(final boolean actual,
			final String message) {
		assertTrue(!actual, message);
	}
	private static void assertEquals(final int expected, final int actual,
			final String message) {
		checks++;
		if (expected != actual)
			throw new AssertionError(message + ": expected " + expected
					+ " but was " + actual);
	}
	private static void assertEquals(final long expected, final long actual,
			final String message) {
		checks++;
		if (expected != actual)
			throw new AssertionError(message + ": expected " + expected
					+ " but was " + actual);
	}
	private static void assertEquals(final String expected,
			final String actual, final String message) {
		checks++;
		if (!expected.equals(actual))
			throw new AssertionError(message + ": expected " + expected
					+ " but was " + actual);
	}
	private static void assertSame(final Object expected, final Object actual,
			final String message) {
		checks++;
		if (expected != actual)
			throw new AssertionError(message);
	}
	private static void assertContains(final String actual,
			final String expectedPart, final String message) {
		checks++;
		if (!actual.contains(expectedPart))
			throw new AssertionError(message + ": missing " + expectedPart);
	}
	private static void expectException(
			final Class<? extends Throwable> expectedType,
			final CheckedAction action, final String message) {
		checks++;
		try {
			action.run();
		} catch (Throwable actual) {
			if (expectedType.isInstance(actual))
				return;
			throw new AssertionError(message + ": expected "
					+ expectedType.getSimpleName() + " but caught "
					+ actual.getClass().getSimpleName());
		}
		throw new AssertionError(message + ": expected "
				+ expectedType.getSimpleName());
	}
	private static Path testDirectory(final String prefix) throws IOException {
		Path root = Paths.get("out", "records-tests");
		Files.createDirectories(root);
		return Files.createTempDirectory(root, prefix);
	}
}
