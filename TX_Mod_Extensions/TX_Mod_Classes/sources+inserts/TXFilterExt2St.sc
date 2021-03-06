// Copyright (C) 2005  Paul Miller. This file is part of TX Modular system distributed under the terms of the GNU General Public License (see file LICENSE).

TXFilterExt2St : TXModuleBase {		// Filter module 

	classvar <>arrInstances;	
	classvar <defaultName;  		// default module name
	classvar <moduleRate;			// "audio" or "control"
	classvar <moduleType;			// "source", "insert", "bus",or  "channel"
	classvar <noInChannels;			// no of input channels 
	classvar <arrAudSCInBusSpecs; 	// audio side-chain input bus specs 
	classvar <>arrCtlSCInBusSpecs; 	// control side-chain input bus specs
	classvar <noOutChannels;		// no of output channels 
	classvar <arrOutBusSpecs; 		// output bus specs
	classvar	<arrBufferSpecs;
	classvar	<guiWidth=500;
	classvar	arrFreqRangePresets;
	
*initClass{
	arrInstances = [];		
	//	set class specific variables
	defaultName = "Filter St";
	moduleRate = "audio";
	moduleType = "insert";
	noInChannels = 2;			
	arrCtlSCInBusSpecs = [ 
		["Frequency", 1, "modfreq", 0],
		["Smooth Time", 1, "modLag", 0],
		["Resonance", 1, "modres", 0],
		["Saturation", 1, "modsat", 0],
		["Dry-Wet Mix", 1, "modWetDryMix", 0]
	];	
	noOutChannels = 2;
	arrOutBusSpecs = [ 
		["Out L + R", [0,1]], 
		["Out L only", [0]], 
		["Out R only", [1]] 
	];	
	arrFreqRangePresets = TXFilter.arrFreqRanges;
} 

*new{ arg argInstName;
	 ^super.new.init(argInstName);
} 

init {arg argInstName;
	//	set  class specific instance variables
	arrSynthArgSpecs = [
		["in", 0, 0],
		["out", 0, 0],
		["freq", 0.5, defLagTime],
		["freqMin",40, defLagTime],
		["freqMax", 20000, defLagTime],
		["lag", 0.5, defLagTime],
		["lagMin", 0.01, defLagTime], 
		["lagMax", 1, defLagTime],
		["res", 0.5, defLagTime],
		["resMin", 0,  defLagTime],
		["resMax", 1, defLagTime],
		["sat", 0.5, defLagTime],
		["satMin", 0,  defLagTime],
		["satMax", 1, defLagTime],
		["wetDryMix", 1.0, defLagTime],
		["modfreq", 0, defLagTime],
		["modLag", 0, defLagTime],
		["modres", 0, defLagTime],
		["modsat", 0, defLagTime],
		["modWetDryMix", 0, defLagTime],
	]; 
	arrOptions = [0, 0, 0];
	arrOptionData = [
		TXFilter.arrOptionData,
		[
			["None", 
				{arg input, lagtime; input;}
			],
			["Linear", 
				{arg input, lagtime; Ramp.kr(input, lagtime); }
			],
			["Exp 1", 
				{arg input, lagtime; Lag.kr(input, lagtime); }
			],
			["Exp 2", 
				{arg input, lagtime; Lag2.kr(input, lagtime); }
			],
			["Exp 3", 
				{arg input, lagtime; Lag3.kr(input, lagtime); }
			],
		],
		TXLevelControl.arrOptionData,
	];
	synthDefFunc = { arg in, out, freq, freqMin, freqMax, lag, lagMin, lagMax, 
			res, resMin, resMax, sat, satMin, satMax, wetDryMix, modfreq = 0.0, modLag = 0.0, 
			modres = 0.0, modsat = 0.0, modWetDryMix = 0.0;
		var input, filterFunction, lagFunction, outFilter, outClean, sumfreq, sumlag, sumres, 
			sumsat, levelControlFunc, mixCombined;
		input = TXClean.ar(TXEnvPresets.startEnvFunc.value * InFeedback.ar(in,2));
		sumfreq = ( (freqMax/ freqMin) ** ((freq + modfreq).max(0.001).min(1)) ) * freqMin;
		sumlag = ( (lagMax/lagMin) ** ((lag + modLag).max(0.001).min(1)) ) * lagMin;
		sumres =  resMin + ( (resMax - resMin) * (res + modres).max(0).min(1) );
		sumsat =  satMin + ( (satMax - satMin) * (sat + modsat).max(0).min(1) );
		mixCombined = (wetDryMix + modWetDryMix).max(0).min(1);
		filterFunction = arrOptionData.at(0).at(arrOptions.at(0)).at(1);
		lagFunction = arrOptionData.at(1).at(arrOptions.at(1)).at(1);
		levelControlFunc = arrOptionData.at(2).at(arrOptions.at(2)).at(1);
		outFilter = TXClean.ar(filterFunction.value(
			input, 
			lagFunction.value(sumfreq, sumlag).max(30),  // limit min freq to 30 
			sumres, 
			sumsat
		));
		Out.ar(out, (levelControlFunc.value(outFilter, input) * mixCombined) + (input * (1-mixCombined)) );
	};
	guiSpecArray = [
		["SynthOptionPopupPlusMinus", "Filter", arrOptionData, 0], 
		["SpacerLine", 4], 
		["TXMinMaxFreqNoteSldr", "Frequency", ControlSpec(0.midicps, 20000, \exponential), 
			"freq", "freqMin", "freqMax", nil, arrFreqRangePresets], 
		["SpacerLine", 4], 
		["SynthOptionPopupPlusMinus", "Smoothing", arrOptionData, 1], 
		["SpacerLine", 4], 
		["TXMinMaxSliderSplit", "Smooth time", ControlSpec(0.0001, 30, \exp, 0, 1, units: " secs"), 
			"lag", "lagMin", "lagMax"], 
		["SpacerLine", 4], 
		["TXMinMaxSliderSplit", "Resonance", ControlSpec(0, 1), "res", "resMin", "resMax"], 
		["SpacerLine", 4], 
		["TXMinMaxSliderSplit", "Saturation", ControlSpec(0, 1), "sat", "satMin", "satMax"], 
		["SpacerLine", 4], 
		["SynthOptionPopupPlusMinus", "Level control", arrOptionData, 2], 
		["SpacerLine", 4], 
		["WetDryMixSlider"], 
	];
	arrActionSpecs = this.buildActionSpecs(guiSpecArray);	
	//	use base class initialise 
	this.baseInit(this, argInstName);
	//	make buffers, load the synthdef and create the synth
	this.makeBuffersAndSynth(arrBufferSpecs);
}

loadExtraData {arg argData;
	// override default method 
	// for older systems, set level control function to tanh
	if (system.dataBank.savedSystemRevision < 1001, {
		fork {
			system.server.sync;
			arrOptions.put(2, 4);
			this.rebuildSynth;
		}
	});
}

}

