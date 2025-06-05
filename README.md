# Love Journey Game

This repository includes three Python scripts for celebrating your relationship:

- `love_journey.py` – a text-based treasure hunt of memories and wishes.
- `love_journey_3d.py` – a small 3D version built with the Ursina game engine.
- `city_walk_3d.py` – explore a procedurally generated city in first person.

## Running the Text Game

Ensure you have Python 3 installed. Run the script from the repository root:

```bash
./love_journey.py
```

Follow the prompts to play the game.

## Running the 3D Game

The 3D version requires the `ursina` package. Install it with:

```bash
pip install ursina
```

Then start the game:

```bash
./love_journey_3d.py
```

Move with the standard WASD keys and press `E` when near a stage to see
its message. Feel free to expand the world with your own models and
romantic surprises.

## Running the City Game

The city exploration game also relies on `ursina`. After installing the
package, launch it with:

```bash
./city_walk_3d.py
```

Wander through a procedurally generated cityscape using the first-person
controller.
