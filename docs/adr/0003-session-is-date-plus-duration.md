# A Session is a date plus a duration, not start and end timestamps

A Session stores only which day it belongs to and how long it lasted. Recording "how much I did" is less work than recording "when I started and stopped". It also avoids edge cases such as Sessions that cross the day boundary or overlap each other. This matches the Discord immersion bot, which works well in practice. The cost: time-of-day stats ("when do I focus best?") are impossible, and past time-of-day information can never be recovered. That was accepted on purpose, because those stats aren't wanted.
