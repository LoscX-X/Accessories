$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runRoot = Join-Path $project ('build/lifecycle-probes/' + [guid]::NewGuid().ToString('N'))
$generated = Join-Path $runRoot 'generated'
$classes = Join-Path $runRoot 'classes'
New-Item -ItemType Directory -Force -Path $generated, $classes | Out-Null
# Actual production storage and migration code; only Bukkit, SQL and serialization boundaries are doubles.
$stubs = @{
'org/bukkit/plugin/java/JavaPlugin.java' = @'
package org.bukkit.plugin.java;
public class JavaPlugin {
 public java.io.File folder;
 public java.io.File getDataFolder(){return folder;}
 public java.util.logging.Logger getLogger(){return java.util.logging.Logger.getLogger("probe");}
 public boolean isEnabled(){return true;}
 public org.bukkit.configuration.file.YamlConfiguration getConfig(){return new org.bukkit.configuration.file.YamlConfiguration();}
}
'@
'org/bukkit/inventory/ItemStack.java' = @'
package org.bukkit.inventory;
public class ItemStack implements Cloneable {
 public String id; public int amount=1;
 public ItemStack(String id){this.id=id;}
 public ItemStack clone(){ItemStack out=new ItemStack(id);out.amount=amount;return out;}
}
'@
'org/bukkit/configuration/file/YamlConfiguration.java' = @'
package org.bukkit.configuration.file;
import java.io.*;import java.nio.file.*;import java.util.*;import org.bukkit.inventory.ItemStack;
// Deterministic test codec with real file I/O, NOT a Bukkit/YAML compatibility test.
public class YamlConfiguration {
 private List<?> contents; private int format=1;
 public void set(String key,Object value){if(key.equals("format"))format=(int)value;else contents=(List<?>)value;}
 public List<?> getList(String key){return contents;}
 public int getInt(String key,int fallback){return format;}
 public List<Integer> getIntegerList(String key){return List.of(9,9);}
 public String saveToString(){StringBuilder s=new StringBuilder("F"+format+"\n");for(Object v:contents){
  if(v==null)s.append("-\n");else{ItemStack i=(ItemStack)v;s.append(i.id).append(':').append(i.amount).append('\n');}}return s.toString();}
 public void loadFromString(String raw){List<ItemStack> out=new ArrayList<>();String[] lines=raw.split("\n");
  if(lines.length<1||!lines[0].startsWith("F"))throw new IllegalArgumentException("bad fixture");
  format=Integer.parseInt(lines[0].substring(1));
  for(int n=1;n<lines.length;n++){String line=lines[n];
  if(line.equals("-")){out.add(null);continue;}String[] p=line.split(":");if(p.length!=2)throw new IllegalArgumentException("bad fixture");
  ItemStack i=new ItemStack(p[0]);i.amount=Integer.parseInt(p[1]);out.add(i);}contents=out;}
 public void save(File file)throws IOException{Files.writeString(file.toPath(),saveToString());}
 public void load(File file)throws IOException{loadFromString(Files.readString(file.toPath()));}
}
'@
'com/blanoir/accessory/database/mysql/SqlManager.java' = @'
package com.blanoir.accessory.database.mysql;
import java.util.*;import java.util.concurrent.*;import org.bukkit.plugin.java.JavaPlugin;
public class SqlManager {
 public final Map<UUID,String> rows=new ConcurrentHashMap<>();
 public volatile boolean failReads,failWrites,failDeletes,closed;
 public void init(String h,int p,String d,String u,String pass,int pool,int idle,int max,int time,int idleTime){}
 public SqlManager(JavaPlugin plugin){}
 public String loadInventory(UUID id){if(failReads||closed)throw new IllegalStateException("injected read failure");return rows.get(id);}
 public void saveInventory(UUID id,String data){if(failWrites||closed)throw new IllegalStateException("injected write failure");rows.put(id,data);}
 public void deleteInventory(UUID id){if(failDeletes||closed)throw new IllegalStateException("injected delete failure");rows.remove(id);}
 public void shutdown(){closed=true;}
}
'@
'com/blanoir/accessory/config/AccessorySettings.java' = @'
package com.blanoir.accessory.config;
import com.blanoir.accessory.module.inventory.AccessoryStore;
public class AccessorySettings {
 public record Storage(AccessoryStore.StorageType type,Mysql mysql){}
 public record Mysql(String host,int port,String database,String username,String password,int poolSize,int minIdle,int maxLifetime,int connectionTimeout,int idleTimeout){}
}
'@
'com/blanoir/accessory/Accessory.java' = @'
package com.blanoir.accessory;
public class Accessory extends org.bukkit.plugin.java.JavaPlugin {
 public static class Debug { public void trace(String category,String key,java.util.function.Supplier<String> message){} }
 public Debug debug(){return new Debug();}
 public com.blanoir.accessory.module.inventory.AccessoryPageManager pageManager(){return new com.blanoir.accessory.module.inventory.AccessoryPageManager();}
}
'@
'com/blanoir/accessory/module/inventory/AccessoryPageManager.java' = @'
package com.blanoir.accessory.module.inventory;
public class AccessoryPageManager {public int pageCount(){return 2;}public int pageSize(int page){return 9;}}
'@
'org/bukkit/Bukkit.java' = @'
package org.bukkit;
import java.util.concurrent.*;
public class Bukkit {
 public static final Scheduler SCHEDULER=new Scheduler();
 public static Scheduler getScheduler(){return SCHEDULER;}
 public static class Scheduler {
  public final BlockingQueue<Runnable> tasks=new LinkedBlockingQueue<>();
  public void runTask(Object plugin,Runnable task){tasks.add(task);}
  public void drain(){Runnable r;while((r=tasks.poll())!=null)r.run();}
 }
}
'@
}
foreach ($entry in $stubs.GetEnumerator()) {
    $file = Join-Path $generated $entry.Key
    New-Item -ItemType Directory -Force -Path (Split-Path $file) | Out-Null
    [IO.File]::WriteAllText($file, $entry.Value)
}
$sources = @(
    'src/main/java/com/blanoir/accessory/module/inventory/AccessoryStore.java',
    'src/main/java/com/blanoir/accessory/module/inventory/AccessoryStorage.java',
    'src/main/java/com/blanoir/accessory/module/inventory/InventorySlotLayout.java',
    'tests/lifecycle-probes/LifecycleProbes.java'
) | ForEach-Object { Join-Path $project $_ }
$sources += Get-ChildItem $generated -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
$javacArguments = @('--release', '25', '-encoding', 'UTF-8', '-d', $classes) + $sources
& javac @javacArguments
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed' }
& java -cp $classes LifecycleProbes $runRoot | Tee-Object -FilePath (Join-Path $runRoot 'results.txt')
if ($LASTEXITCODE -ne 0) { throw 'Storage regression failed' }
Write-Output "Probe artifacts: $runRoot"
