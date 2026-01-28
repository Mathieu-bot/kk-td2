package org.example.service;

import java.util.HashMap;
import java.util.Map;
import org.example.model.Unit;

public final class UnitConversionService {

  private static final class ConversionConfig {
    private final Double pcsPerKg;
    private final Double litersPerKg;

    private ConversionConfig(Double pcsPerKg, Double litersPerKg) {
      this.pcsPerKg = pcsPerKg;
      this.litersPerKg = litersPerKg;
    }
  }

  private static final Map<Integer, ConversionConfig> CONFIGS = new HashMap<>();

  static {
    CONFIGS.put(2, new ConversionConfig(10.0, null));
    CONFIGS.put(1, new ConversionConfig(2.0, null));
    CONFIGS.put(4, new ConversionConfig(10.0, 2.5));
    CONFIGS.put(3, new ConversionConfig(8.0, null));
    CONFIGS.put(5, new ConversionConfig(4.0, 5.0));
  }

  private UnitConversionService() {}

  public static double convert(int ingredientId, double quantity, Unit from, Unit to) {
    if (from == null || to == null) {
      throw new IllegalArgumentException("from and to units must not be null");
    }

    if (Double.isNaN(quantity) || Double.isInfinite(quantity)) {
      throw new IllegalArgumentException("quantity must be a finite number");
    }

    if (from == to) {
      return quantity;
    }

    double quantityInKg = toKg(ingredientId, quantity, from);

    if (to == Unit.KG) {
      return quantityInKg;
    }

    return fromKg(ingredientId, quantityInKg, to);
  }

  private static double toKg(int ingredientId, double quantity, Unit from) {
    if (from == Unit.KG) {
      return quantity;
    }

    ConversionConfig config = CONFIGS.get(ingredientId);
    if (config == null) {
      throw new IllegalArgumentException(
          "No unit conversion configuration for ingredient id=" + ingredientId);
    }

    if (from == Unit.PCS) {
      if (config.pcsPerKg == null || config.pcsPerKg == 0.0) {
        throw new IllegalArgumentException(
            "Conversion from PCS to KG not supported for ingredient id=" + ingredientId);
      }
      return quantity / config.pcsPerKg;
    }

    if (from == Unit.L) {
      if (config.litersPerKg == null || config.litersPerKg == 0.0) {
        throw new IllegalArgumentException(
            "Conversion from L to KG not supported for ingredient id=" + ingredientId);
      }
      return quantity / config.litersPerKg;
    }

    throw new IllegalArgumentException("Unsupported from unit: " + from);
  }

}
