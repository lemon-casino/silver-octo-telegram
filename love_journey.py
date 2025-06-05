#!/usr/bin/env python3
"""Love Journey: A simple interactive anniversary game."""

from textwrap import dedent


def ask(question: str) -> str:
    """Prompt the user and return their response."""
    print()
    return input(question + "\n> ")


def memory_lane():
    print(dedent(
        """
        \nStage 1: Memory Lane
        Let's reminisce about some of our favorite moments together.
        Answer the questions to earn your small surprises!
        """
    ))
    questions = [
        "Where did we go on our first date?",
        "What movie did we watch together early on?",
        "What is our favorite restaurant?",
    ]
    for q in questions:
        ask(q)
        print("That brings back memories!\n")


def challenge_of_hearts():
    print(dedent(
        """
        \nStage 2: Challenge of Hearts
        Time for a few fun mini-games! Let's see how well we know each other.
        """
    ))
    trivia = [
        ("What's my favorite color?", "blue"),
        ("Name a song that always reminds me of you.", None),
    ]
    for q, answer in trivia:
        response = ask(q)
        if answer and response.lower() == answer:
            print("Correct!\n")
        else:
            print("I love that answer!\n")
    print("Collect your puzzle pieces!\n")


def secret_wishes():
    print(dedent(
        """
        \nStage 3: Secret Wishes
        Let's share hopes for our future together.
        """
    ))
    my_wish = "I wish for many more adventures with you."
    ask("Write down your wish for us and press Enter when done.")
    print("\nI'll keep our wishes safe in a special box.")
    print(f"My wish: {my_wish}\n")


def starlit_promises():
    print(dedent(
        """
        \nStage 4: Starlit Promises
        Imagine we're under the stars, exchanging promises.
        """
    ))
    promises = [
        "One future picnic date",
        "A weekend getaway",
        "I'll always be there for you",
    ]
    for p in promises:
        print(f"Promise token: {p}")
    ask("Press Enter when you're ready for the finale.")


def grand_finale():
    print(dedent(
        """
        \nStage 5: The Grand Finale
        You found the treasure chest! Inside is something special just for you.
        Let's share a slow dance and enjoy dessert together.
        """
    ))


if __name__ == "__main__":
    print("Welcome to Love Journey! Happy Anniversary!\n")
    memory_lane()
    challenge_of_hearts()
    secret_wishes()
    starlit_promises()
    grand_finale()
    print("\nThank you for playing! I love you.")
