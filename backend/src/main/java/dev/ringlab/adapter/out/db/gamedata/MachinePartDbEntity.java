package dev.ringlab.adapter.out.db.gamedata;

import dev.ringlab.domain.gamedata.MachinePartType;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "machine_parts")
public class MachinePartDbEntity {
  @Id public UUID id;

  @Column(name = "source_machine_id", nullable = false)
  public UUID sourceMachineId;

  @Enumerated(EnumType.STRING)
  @Column(name = "part_type", nullable = false, length = 5)
  public MachinePartType type;
}
