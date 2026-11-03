package currency;

/**
 * Manages the player's in-game currency balance.
 *
 * Ensures that the balance never becomes negative
 * and prevents invalid transactions or integer overflow.
 *
 * @author Joshua Hernandez Ruiz
 */
public class CurrencyBalance {

    private int balance;

    /**
     * Creates a currency balance initialized to zero.
     */
    public CurrencyBalance() {
        this.balance = 0;
    }

    /**
     * Creates a currency balance with an initial amount.
     *
     * @param initialBalance initial amount of currency
     * @throws IllegalArgumentException if initialBalance is negative
     */
    public CurrencyBalance(int initialBalance) {
        if (initialBalance < 0) {
            throw new IllegalArgumentException(
                "Initial balance cannot be negative."
            );
        }

        this.balance = initialBalance;
    }

    /**
     * Returns the current currency balance.
     *
     * @return current balance
     */
    public int getBalance() {
        return balance;
    }

    /**
     * Checks whether the player has enough currency.
     *
     * @param amount amount required
     * @return true if the amount is positive and affordable
     */
    public boolean canAfford(int amount) {
        return amount > 0 && balance >= amount;
    }

    /**
     * Adds currency to the player's balance.
     *
     * @param amount amount to add
     * @return true if the operation succeeds
     */
    public boolean add(int amount) {

        if (amount <= 0) {
            return false;
        }

        if (amount > Integer.MAX_VALUE - balance) {
            return false;
        }

        balance += amount;
        return true;
    }

    /**
     * Deducts currency from the player's balance.
     *
     * @param amount amount to deduct
     * @return true if the operation succeeds
     */
    public boolean deduct(int amount) {

        if (!canAfford(amount)) {
            return false;
        }

        balance -= amount;
        return true;
    }
}
