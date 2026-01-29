package org.example.model;

import java.time.Instant;

public class TableOrder {

  private final Table table;
  private final Instant arrivalDatetime;
  private final Instant departureDatetime;

  public TableOrder(Table table, Instant arrivalDatetime, Instant departureDatetime) {
    this.table = table;
    this.arrivalDatetime = arrivalDatetime;
    this.departureDatetime = departureDatetime;
  }

  public Table getTable() {
    return table;
  }

  public Instant getArrivalDatetime() {
    return arrivalDatetime;
  }

  public Instant getDepartureDatetime() {
    return departureDatetime;
  }
}
