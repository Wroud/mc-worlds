package dev.wroud.mc.worlds.util.filefix.fixes;

import com.mojang.datafixers.schemas.Schema;
import java.util.List;
import net.minecraft.util.filefix.FileFix;
import net.minecraft.util.filefix.access.FileRelation;
import net.minecraft.util.filefix.operations.FileFixOperations;

public class McWorldsDataFileFix extends FileFix {

  public McWorldsDataFileFix(final Schema schema) {
    super(schema);
  }

  @Override
  public void makeFixer() {
    this.addFileFixOperation(
        FileFixOperations.applyInFolders(
            FileRelation.DATA,
            List.of(
                FileFixOperations.move("mc-worlds.dat", "mc-worlds/worlds_data.dat"))));
  }
}
