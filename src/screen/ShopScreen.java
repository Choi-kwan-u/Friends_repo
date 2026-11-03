package screen;

import audio.AudioManager;
import currency.PlayerAssetsState;
import currency.PurchaseResult;
import currency.ShopCatalog.Category;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import shop.ShopController;
import shop.ShopController.SaveStatus;
import shop.ShopProduct;

/** Displays products using the shared session controller. */
public class ShopScreen extends Screen {
    public static final int RETURN_TO_MAIN_MENU = 1;

    private final ShopController controller;
    private final List<ShopProduct> upgrades = new ArrayList<ShopProduct>();
    private final List<ShopProduct> unlocks = new ArrayList<ShopProduct>();
    private PlayerAssetsState assets;
    /** 0: stat upgrades, 1: permanent unlocks, 2: footer actions. */
    private int selectedSection;
    private int upgradeIndex;
    private int unlockIndex;
    /** 0: Back, 1: Retry save (only while a save is pending). */
    private int selectedAction;
    private PurchaseResult lastResult;
    private boolean lastProductAvailableByDefault;
    private boolean upHeld;
    private boolean downHeld;
    private boolean leftHeld;
    private boolean rightHeld;
    private boolean confirmHeld;
    private boolean backHeld;
    private boolean retryHeld;

    public ShopScreen(int width, int height, int fps, ShopController controller) {
        super(validateArguments(width, height, fps, controller), height, fps);
        this.controller = controller;
        refreshProducts();
        this.returnCode = RETURN_TO_MAIN_MENU;
    }

    private void refreshProducts() {
        upgrades.clear();
        unlocks.clear();
        for (ShopProduct product : controller.getProducts()) {
            if (product.getCategory() == Category.UPGRADE) upgrades.add(product);
            else unlocks.add(product);
        }
        upgradeIndex = Math.max(0, Math.min(upgradeIndex, upgrades.size() - 1));
        unlockIndex = Math.max(0, Math.min(unlockIndex, unlocks.size() - 1));
    }

    private static int validateArguments(int width, int height, int fps, ShopController controller) {
        if (width <= 0 || height <= 0 || fps <= 0 || controller == null) {
            throw new IllegalArgumentException("Shop requires positive dimensions/FPS and a controller.");
        }
        return width;
    }

    @Override
    public void initialize() {
        refreshProducts();
        assets = controller.snapshot();
        selectedSection = !upgrades.isEmpty() ? 0 : !unlocks.isEmpty() ? 1 : 2;
        upgradeIndex = 0;
        unlockIndex = 0;
        selectedAction = 0;
        lastResult = null;
        lastProductAvailableByDefault = false;
        inputDelay.reset();

        AudioManager.playBGM("audio/bgm/music_shop.wav");

        // Consume held state on entry, including a confirm key from the Main Menu.
        processInput(false);
    }

    @Override
    public int run() {
        super.run();
        return RETURN_TO_MAIN_MENU;
    }

    @Override
    protected void update() {
        super.update();
        processInput(inputDelay.checkFinished());
        draw();
    }

    /** Track key edges even during the entry delay; never replay a held purchase. */
    void processInput(boolean acceptsInput) {
        boolean up = inputManager.isKeyDown(KeyEvent.VK_UP) || inputManager.isKeyDown(KeyEvent.VK_W);
        boolean down = inputManager.isKeyDown(KeyEvent.VK_DOWN) || inputManager.isKeyDown(KeyEvent.VK_S);
        boolean left = inputManager.isKeyDown(KeyEvent.VK_LEFT) || inputManager.isKeyDown(KeyEvent.VK_A);
        boolean right = inputManager.isKeyDown(KeyEvent.VK_RIGHT) || inputManager.isKeyDown(KeyEvent.VK_D);
        boolean confirm = inputManager.isKeyDown(KeyEvent.VK_SPACE) || inputManager.isKeyDown(KeyEvent.VK_ENTER);
        boolean back = inputManager.isKeyDown(KeyEvent.VK_ESCAPE);
        boolean retry = inputManager.isKeyDown(KeyEvent.VK_R);
        boolean moveUp = up && !upHeld;
        boolean moveDown = down && !downHeld;
        boolean moveLeft = left && !leftHeld;
        boolean moveRight = right && !rightHeld;
        boolean select = confirm && !confirmHeld;
        boolean leave = back && !backHeld;
        boolean retryPressed = retry && !retryHeld;
        upHeld = up;
        downHeld = down;
        leftHeld = left;
        rightHeld = right;
        confirmHeld = confirm;
        backHeld = back;
        retryHeld = retry;
        if (!acceptsInput) {
            return;
        }

        if (controller.getSaveStatus() != SaveStatus.FAILED) selectedAction = 0;
        if (leave) {
            leaveShop();
        } else if (moveUp || moveDown) {
            moveSection(moveUp ? -1 : 1);
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (moveLeft || moveRight) {
            moveWithinSection(moveLeft ? -1 : 1);
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (retryPressed && controller.getSaveStatus() == SaveStatus.FAILED) {
            retrySave();
        } else if (select) {
            if (selectedSection == 2) {
                if (selectedAction == 1) retrySave();
                else leaveShop();
            } else {
                buySelectedProduct();
            }
        }
    }

    private void moveSection(int direction) {
        do {
            selectedSection = (selectedSection + direction + 3) % 3;
        } while ((selectedSection == 0 && upgrades.isEmpty())
                || (selectedSection == 1 && unlocks.isEmpty()));
    }

    private void moveWithinSection(int direction) {
        if (selectedSection == 0) {
            upgradeIndex = (upgradeIndex + direction + upgrades.size()) % upgrades.size();
        } else if (selectedSection == 1) {
            unlockIndex = (unlockIndex + direction + unlocks.size()) % unlocks.size();
        } else if (controller.getSaveStatus() == SaveStatus.FAILED) {
            selectedAction = 1 - selectedAction;
        }
    }

    private void buySelectedProduct() {
        ShopProduct product = selectedSection == 0 ? upgrades.get(upgradeIndex) : unlocks.get(unlockIndex);
        lastProductAvailableByDefault = product.isAvailableByDefault();
        // Refresh before the ownership check in case another game-loop action changed assets.
        assets = controller.snapshot();
        if (assets.getUnlockedContentIds().contains(product.getId())) {
            lastResult = PurchaseResult.ALREADY_UNLOCKED;
        } else {
            lastResult = controller.purchase(product.getId());
        }
        assets = controller.snapshot();
        refreshProducts();

        if (lastProductAvailableByDefault || lastResult == PurchaseResult.SUCCESS) {
            AudioManager.playSFX("audio/sfx/shop_successful_purchase.wav");
        } else {
            AudioManager.playSFX("audio/sfx/shop_purchase_failure.wav");
        }
    }

    private void retrySave() {
        controller.retrySave();
        assets = controller.snapshot();
        if (controller.getSaveStatus() != SaveStatus.FAILED) selectedAction = 0;
        AudioManager.playSFX("audio/sfx/menu_select.wav");
    }

    private void leaveShop() {
        AudioManager.playSFX("audio/sfx/menu_back.wav");
        returnCode = RETURN_TO_MAIN_MENU;
        isRunning = false;
    }

    protected void draw() {
        drawManager.initDrawing(this);
        drawManager.drawShop(this, upgrades, unlocks, assets, selectedSection,
                upgradeIndex, unlockIndex, selectedAction,
                lastProductAvailableByDefault ? "Available in Hangar. No purchase needed." : resultMessage(lastResult),
                lastProductAvailableByDefault || lastResult == PurchaseResult.SUCCESS,
                controller.getSaveStatus());
        drawManager.completeDrawing(this);
    }

    static String resultMessage(PurchaseResult result) {
        if (result == null) return "";
        switch (result) {
        case SUCCESS: return "Purchase complete.";
        case INSUFFICIENT_FUNDS: return "Not enough credits.";
        case ALREADY_UNLOCKED: return "Already owned. No credits spent.";
        case INVALID_ITEM_ID: return "Invalid product. Nothing purchased.";
        case CATEGORY_MISMATCH: return "Unknown product or wrong category.";
        case ITEM_NOT_AVAILABLE: return "This product is unavailable.";
        default: return "Purchase could not be completed.";
        }
    }
}
