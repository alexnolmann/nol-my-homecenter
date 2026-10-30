"""Create the bundled API-19-compatible display font from Google Fonts' variable TTF.

Usage: python build-poster-font.py source.ttf output.ttf
Requires fonttools==4.67.0; source and licence URLs are in assets/fonts/README.md.
"""
import sys
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools import subset

font = instantiateVariableFont(TTFont(sys.argv[1]), {"wght": 900}, inplace=True)
options = subset.Options()
options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]
options.name_legacy = True
options.name_languages = [0x409]
subsetter = subset.Subsetter(options=options)
subsetter.populate(unicodes=list(range(0x20, 0x250)) + list(range(0x400, 0x530))
                   + list(range(0x2000, 0x2070)) + [0x20AC, 0x20BD, 0x2116, 0x2212, 0x25B2, 0x25BC])
subsetter.subset(font)
font.save(sys.argv[2])
