package org.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.example.model.Unit;
import org.junit.jupiter.api.Test;

class UnitConversionServiceTest {

  @Test
  void testConvertToKg_accordingToConfigurationTable() {
    // Laitue (id=1): 1 KG = 2 PCS -> 2 PCS OUT = 1.0 KG
    assertEquals(1.0, UnitConversionService.convert(1, 2.0, Unit.PCS, Unit.KG), 0.0001);

    // Tomate (id=2): 1 KG = 10 PCS -> 5 PCS OUT = 0.5 KG
    assertEquals(0.5, UnitConversionService.convert(2, 5.0, Unit.PCS, Unit.KG), 0.0001);

    // Poulet (id=3): 1 KG = 8 PCS -> 4 PCS OUT = 0.5 KG
    assertEquals(0.5, UnitConversionService.convert(3, 4.0, Unit.PCS, Unit.KG), 0.0001);

    // Chocolat (id=4): 1 KG = 2.5 L -> 1 L OUT = 0.4 KG
    assertEquals(0.4, UnitConversionService.convert(4, 1.0, Unit.L, Unit.KG), 0.0001);

    // Beurre (id=5): 1 KG = 5 L -> 1 L OUT = 0.2 KG
    assertEquals(0.2, UnitConversionService.convert(5, 1.0, Unit.L, Unit.KG), 0.0001);
  }

  @Test
  void testStockScenario_withMixedUnits_matchesExpectedFinalStocks() {
    double laitueInitial = 5.0;
    double tomateInitial = 4.0;
    double pouletInitial = 10.0;
    double chocolatInitial = 3.0;
    double beurreInitial = 2.5;

    double laitueOutKg = UnitConversionService.convert(1, 2.0, Unit.PCS, Unit.KG);
    double tomateOutKg = UnitConversionService.convert(2, 5.0, Unit.PCS, Unit.KG);
    double chocolatOutKg = UnitConversionService.convert(4, 1.0, Unit.L, Unit.KG);
    double pouletOutKg = UnitConversionService.convert(3, 4.0, Unit.PCS, Unit.KG);
    double beurreOutKg = UnitConversionService.convert(5, 1.0, Unit.L, Unit.KG);

    assertEquals(1.0, laitueOutKg, 0.0001); // Laitue
    assertEquals(0.5, tomateOutKg, 0.0001); // Tomate
    assertEquals(0.5, pouletOutKg, 0.0001); // Poulet
    assertEquals(0.4, chocolatOutKg, 0.0001); // Chocolat
    assertEquals(0.2, beurreOutKg, 0.0001); // Beurre

    // Stock final
    assertEquals(4.0, laitueInitial - laitueOutKg, 0.0001);
    assertEquals(3.5, tomateInitial - tomateOutKg, 0.0001);
    assertEquals(9.5, pouletInitial - pouletOutKg, 0.0001);
    assertEquals(2.6, chocolatInitial - chocolatOutKg, 0.0001);
    assertEquals(2.3, beurreInitial - beurreOutKg, 0.0001);
  }

  @Test
  void testUnsupportedConversion_throwsMeaningfulException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> UnitConversionService.convert(2, 1.0, Unit.KG, Unit.L));
  }
}
