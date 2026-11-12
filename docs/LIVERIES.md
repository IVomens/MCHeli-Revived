# Custom liveries

Players can paint their own textures for any MCHeli vehicle, upload them to the server and put
them on the vehicle they fly. Everyone on the server sees the livery. The textures that come with
the vehicles stay the default, and a vehicle can always go back to its default texture.

## For players

### Opening the livery screen

Bind a key to **Vehicle Liveries** (Options → Controls → Key Binds, category *MCHeli*). The key is
unbound by default. Get into a vehicle and press it.

The screen has three tabs:

* **This vehicle**: every livery uploaded for this vehicle type, by anyone on the server.
* **My liveries**: everything you uploaded, for any vehicle type.
* **Upload**: the PNG files in your livery folder, ready to send.

Buttons:

| Button | What it does |
|---|---|
| Apply | Paints the selected livery on your vehicle. |
| Use default | Puts the vehicle's original texture back. |
| Delete | Deletes the selected livery from the server (your own only; operators can delete any). Vehicles that use it go back to their default texture. |
| Upload... / Upload | Switches to the Upload tab; there it sends the selected file under the name you typed. |
| Open folder | Opens your livery folder, `<minecraft folder>/mcheli_liveries/`. |
| Get template | Saves the painting templates for this vehicle (see below) and opens the folder. |

Only the pilot can change a vehicle's livery. Operators can also change the livery of any vehicle
within 16 blocks of them.

### Painting a livery

1. Sit in the vehicle, open the livery screen and press **Get template**. Two files appear in
   `mcheli_liveries/templates/`:
   * `<vehicle>_texture.png`: the vehicle's current texture, exactly as the game uses it. Paint
     over this file.
   * `<vehicle>_guide.png`: a UV guide. It shows the texture faded, with the area each model part
     uses outlined in its own colour and labelled on the right (for example "Body", "Rotor 0",
     "Weapon 1"). Use it to find out which part of the image ends up where on the model.
2. Paint over `<vehicle>_texture.png` in any image editor and save the result as a PNG. Keep the
   same proportions so the parts line up; a larger image (twice the size, say) works for finer
   detail, within the server's limits.
3. Put the PNG into `mcheli_liveries/` (or drag and drop it onto the livery screen, which copies it
   there).

### Uploading

Open the **Upload** tab, select the file, type a name and press **Upload**. The progress and the
server's answer are shown at the bottom of the screen. A successful upload shows up in
**My liveries** and, for players flying the same vehicle type, in **This vehicle**.

Uploading the same picture twice does not use extra space; the server recognises it.

## For server owners

Liveries are switched on by default. All options are in `config/mcheli.cfg` and are read on the
server:

| Option | Default | Meaning |
|---|---|---|
| `EnableLiveries` | `true` | Allow uploading, applying and showing liveries at all. When off, every vehicle shows its default texture; the stored liveries are kept for when it is switched on again. |
| `LiveryUploadOperatorsOnly` | `false` | Only operators (permission level 2) may upload. Everyone can still apply the liveries that exist. |
| `LiveryMaxKilobytes` | `2048` | Largest accepted upload, in KB (64 to 16384). |
| `LiveryMaxDimension` | `2048` | Largest accepted width or height, in pixels (64 to 8192). Images must also be at least 16 pixels on each side. |
| `LiveriesPerPlayer` | `10` | How many liveries one player may have at a time. |
| `LiveryStorageMegabytes` | `256` | Disk space all liveries of the world may use together. |

### Storage

Liveries belong to the world and live in `<world>/mcheli_liveries/`:

* `index.json`: one entry per livery (id, vehicle type, name, uploader UUID and name, size, upload
  time).
* `<id>.png`: the image. The id is the SHA-256 of the file.

To remove a livery by hand, stop the server, delete its line from `index.json` and its PNG.
Vehicles in the world only store the livery id (NBT key `Livery`), so a missing livery simply
shows the default texture.

### What the server checks

Uploads come from players, so the server does not trust them:

* Every upload is decoded and saved again as a clean PNG. Anything that is not a readable PNG within
  the size limits is refused with a message to the uploader.
* Uploads arrive in pieces of at most 30 KB, one upload per player at a time. An upload that stalls
  for 60 seconds is dropped.
* A livery can only be applied to the vehicle type it was uploaded for.
* Images are sent to players in small pieces each tick, so a player joining near many painted
  vehicles does not flood their connection.

## Client cache

Downloaded liveries are kept in `<minecraft folder>/mcheli_liveries/cache/` and reused on later
visits. Each file is checked against its id before it is used. The folder can be deleted at any
time. Livery textures that have not been on screen for five minutes are unloaded from memory.
