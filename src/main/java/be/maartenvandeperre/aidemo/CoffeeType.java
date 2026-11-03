package be.maartenvandeperre.aidemo;

import dev.langchain4j.model.output.structured.Description;

/**
 * The coffee menu the decision model chooses from. Each constant is one
 * option of the choice question; the description tells the model what it is.
 *
 * Descriptions say what the drink is in plain terms and avoid words a
 * customer might use for something else: with "long and black" on the
 * americano, "a long meeting ahead" pulled an 8B model to the americano.
 */
public enum CoffeeType {
    @Description("a single shot of black coffee")
    ESPRESSO("Espresso"),
    @Description("a double shot of black coffee, extra strong")
    DOPPIO("Doppio"),
    @Description("espresso diluted with hot water, a large black coffee without milk")
    AMERICANO("Americano"),
    @Description("espresso with a spoon of milk foam, almost black")
    MACCHIATO("Macchiato"),
    @Description("small coffee: half espresso, half warm milk")
    CORTADO("Cortado"),
    @Description("strong milk coffee: double espresso with a little velvety milk")
    FLAT_WHITE("Flat white"),
    @Description("milk coffee with a thick foam top")
    CAPPUCCINO("Cappuccino"),
    @Description("large, mild milk coffee, mostly steamed milk")
    LATTE("Latte"),
    @Description("milk coffee with chocolate, sweet")
    MOCHA("Mocha"),
    @Description("iced coffee, served cold, with caffeine")
    COLD_BREW("Cold brew"),
    @Description("coffee without caffeine, for people avoiding caffeine")
    DECAF("Decaf");

    private final String label;

    CoffeeType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
