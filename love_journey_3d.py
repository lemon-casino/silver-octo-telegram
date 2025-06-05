#!/usr/bin/env python3
"""Love Journey 3D: A small 3D anniversary game using the Ursina engine.

This script creates a simple world where the player can walk around and
interact with romantic memory "stages." When the player approaches a
stage and presses ``E``, a short message appears.

Dependencies
------------
- Python 3
- ``ursina`` (install via ``pip install ursina``)

This is only a minimal demonstration. Feel free to expand the world
with your own models, sounds, and quests.
"""

from ursina import *
from ursina.prefabs.first_person_controller import FirstPersonController


class Stage(Entity):
    """A small trigger entity that shows a text message when activated."""

    def __init__(self, position, message):
        super().__init__(
            model="cube",
            color=color.azure,
            position=position,
            scale=1,
            collider="box",
        )
        self.message = message
        self.activated = False

    def activate(self):
        if self.activated:
            return
        self.activated = True
        txt = Text(
            self.message,
            origin=(0, 0),
            scale=2,
            background=True,
        )
        # Remove the text after a few seconds
        invoke(destroy, txt, delay=5)
        # Hide the stage so it can't be triggered again
        self.disable()


app = Ursina()

# Basic environment
window.title = "Love Journey 3D"
window.borderless = False
Sky()
Ground = Entity(
    model="plane",
    texture="white_cube",
    texture_scale=(50, 50),
    scale=(50, 1, 50),
    color=color.light_gray,
)

player = FirstPersonController(y=1, origin_y=-0.5, speed=5)

# Define the stages
stages = [
    Stage((0, 0, 5), "Stage 1: Memory Lane\nRemember our first date?"),
    Stage((5, 0, 5), "Stage 2: Challenge of Hearts\nAnswer trivia about us!"),
    Stage((10, 0, 5), "Stage 3: Secret Wishes\nShare hopes for our future."),
    Stage((15, 0, 5), "Stage 4: Starlit Promises\nExchange romantic promises."),
    Stage((20, 0, 5), "Stage 5: The Grand Finale\nFinish with a sweet surprise."),
]


def update():
    # Check for stage activation
    for stage in stages:
        if not stage.activated and distance(stage, player) < 2:
            if held_keys["e"]:
                stage.activate()


app.run()
