// Copyright (C) 2011  Paul Miller. This file is part of TX Modular system distributed under the terms of the GNU General Public License (see file LICENSE).

classvar <autonomousPath;    

		//
	}
	*removePopupSpecialCharacters { arg inString, replaceString = "_";
		var newString;
		newString = inString.replace("-", replaceString)
			.replace("<", replaceString)
			.replace("=", replaceString)
			.replace("(", replaceString);
		^newString;
	}
}
