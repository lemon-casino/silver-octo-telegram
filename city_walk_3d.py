#!/usr/bin/env python3
"""City Love Walk 3D: Explore a small procedurally generated city.

This script uses the Ursina engine to create a simple open world with
roads and buildings reminiscent of classic open-world games. Walk around
freely with the first-person controller.
"""

from ursina import *
from ursina.prefabs.first_person_controller import FirstPersonController
import random


class City(Entity):
    """Generate a grid-based city with roads and buildings."""

    def __init__(self, size=20):
        super().__init__()
        for x in range(-size, size):
            for z in range(-size, size):
                if x % 6 == 0 or z % 6 == 0:
                    # Road segment
                    Entity(
                        model="cube",
                        color=color.dark_gray,
                        scale=(1, 0.1, 1),
                        position=(x, 0, z),
                        collider="box",
                    )
                else:
                    # Random building block
                    height = random.uniform(1, 5)
                    hue = random.uniform(0.4, 0.7)
                    Entity(
                        model="cube",
                        color=color.color(hue, 0.7, 0.9),
                        scale=(1, height, 1),
                        position=(x, height / 2, z),
                        collider="box",
                    )


def main():
    app = Ursina()
    window.title = "Love City 3D"
    window.borderless = False

    Sky()
    City(size=20)
    player = FirstPersonController(y=2, origin_y=-0.5, speed=5)

    app.run()


if __name__ == "__main__":
    main()
