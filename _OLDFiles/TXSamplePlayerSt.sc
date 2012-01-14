// Copyright (C) 2005  Paul Miller. This file is part of TX Modular system distributed under the terms of the GNU General Public License (see file LICENSE).

TXSamplePlayerSt : TXModuleBase {

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
	classvar	<guiHeight=450;
	classvar	<guiWidth=450;
	classvar	<guiLeft=100;
	classvar	<guiTop=300;
	
	var <>sampleNo = 0;
	var sampleFileName = "";
	var sampleNumChannels = 0;
	var sampleFreq = 440;

*initClass{
	arrInstances = [];		
	//	set class specific variables
	defaultName = "Sample Player st.";
	moduleRate = "audio";
	moduleType = "groupsource";
	arrCtlSCInBusSpecs = [ 		
		["Sample Start", 1, "modStart", 0],
		["Sample End", 1, "modEnd", 0],
		["Sample Reverse", 1, "modReverse", 0],
		["Delay", 1, "modDelay", 0],
		["Attack", 1, "modAttack", 0],
		["Decay", 1, "modDecay", 0],
		["Sustain level", 1, "modSustain", 0],
		["Sustain time", 1, "modSustainTime", 0],
		["Release", 1, "modRelease", 0],
		["Curve", 1, "modCurve", 0],
	];	
	noOutChannels = 2;
	arrOutBusSpecs = [ 
		["Out L + R", [0,1]], 
		["Out L only", [0]], 
		["Out R only", [1]] 
	];	
	arrBufferSpecs = [ ["bufnumSample", 2048, 2] ];
} // end of method initClass

*new{ arg argInstName;
	 ^super.new.init(argInstName);
} 

init {arg argInstName;
	//	set  class specific instance variables
	arrSynthArgSpecs = [
		["out", 0, 0],
		["gate", 1, 0],
		["note", 0, 0],
		["velocity", 0, 0],
		["keytrack", 1, \ir],
		["transpose", 0, 0],
		["bufnumSample", 0, \ir],
		["sampleNo", 0, \ir],
		["sampleFreq", 440, \ir],
		["start", 0, defLagTime],
		["end", 1, defLagTime],
		["reverse", 0, defLagTime],
		["level", 0.5, \ir],
		["envtime", 0, \ir],
		["delay", 0, \ir],
		["attack", 0, \ir],
		["decay", 0.15, \ir],
		["sustain", 1, \ir],
		["sustainTime", 1, \ir],
		["release", 0, \ir],
		["curve", 0, \ir],
		["timeMultiply", 1, defLagTime],
		["modStart", 0, defLagTime],
		["modEnd", 0, defLagTime],
		["modReverse", 0, defLagTime],
		["modDelay", 0, \ir],
		["modAttack", 0, \ir],
		["modDecay", 0, \ir],
		["modSustain", 0, \ir],
		["modSustainTime", 0, \ir],
		["modRelease", 0, \ir],
		["modCurve", 0, \ir],
  	]; 
  	// create looping option
	arrOptions = [0];
	arrOptionData = [
		[	["Single shot", 
				{arg outRate, bufnumSample, start, end; 
					BufRd.ar(2, bufnumSample, 
						(Sweep.ar(1, outRate * BufSampleRate.kr(bufnumSample))
							+ (((start * outRate.sign.max(0)) + (end * outRate.sign.neg.max(0))) * BufFrames.kr(bufnumSample))
						)
						.min(end * BufFrames.kr(bufnumSample))
						.max(start * BufFrames.kr(bufnumSample))
						,0
					);
				}
			],
			["Looped", 
				{arg outRate, bufnumSample, start, end; 
					BufRd.ar(2, bufnumSample, 
						Phasor.ar(0, outRate * BufRateScale.kr(0), start * BufFrames.kr(0), end * BufFrames.kr(0)),
						0,1
					);
				}
			],
			["X-Fade Looped", 
				{arg outRate, bufnumSample, start, end; 
				Mix.new(
					BufRd.ar(2, bufnumSample, 
						Phasor.ar(1, outRate * BufRateScale.kr(0), start * BufFrames.kr(0), end * BufFrames.kr(0), 
							[start, (end-start)/2] * BufFrames.kr(0))
						
					) * SinOsc.kr(outRate * BufRateScale.kr(0), [0,pi/2]).abs;
				)}
			]
		]
	];
	synthDefFunc = { 
		arg out, gate, note, velocity, keytrack, transpose, bufnumSample, sampleNo, sampleFreq, start, end, reverse, level, 
			envtime=0, delay, attack, decay, sustain, sustainTime, release, curve, timeMultiply, 
			modStart, modEnd, modReverse, modDelay, modAttack, modDecay, modSustain, modSustainTime, modRelease, modCurve;
		var outEnv, outFreq, outRate, outFunction, outSample, curveAdjusted, sStart, sEnd, rev, 
			del, att, dec, sus, sustime, rel;
		
		sStart = (start + modStart).max(0).min(1);
		sEnd = (end + modEnd).max(0).min(1);
		rev = (reverse + modReverse).max(0).min(1);
		del = (delay + modDelay).max(0).min(1);
		att = (attack + modAttack).max(0.001).min(1);
		dec = (decay + modDecay).max(0.001).min(1);
		sus = (sustain + modSustain).max(0).min(1);
		sustime = (sustainTime + modSustainTime).max(0.001).min(1);
		rel = (release + modRelease).max(0.001).min(1);
		curveAdjusted = ((curve) + (modCurve * 10)).max(-10).min(10);
		outEnv = EnvGen.kr(
			Env.dadsr(del, att * timeMultiply, dec* timeMultiply, sus, rel* timeMultiply, 1, curveAdjusted), 
			gate, 
			doneAction: 2
		);
		outFreq = ((note + transpose).midicps * keytrack) + (sampleFreq * (1-keytrack));
		outRate = (outFreq / sampleFreq) * (rev-0.5).neg.sign;
		outFunction = arrOptionData.at(0).at(arrOptions.at(0)).at(1);
		outSample = outFunction.value(outRate, bufnumSample, sStart, sEnd) * level * 2;
		// amplitude is vel *  0.00315 approx. == 1 / 127
		Out.ar(out, outEnv * outSample * (velocity * 0.007874));
	};
	// array of sample filenames - beginning with blank sample  - only show stereo files
	guiSpecArray = [
		["TXPopupAction", "Sample", {["No Sample"]++system.sampleBankStereo
		.collect({arg item, i; 
	//		item.at(0).keep(-50);
			item.at(0).basename;
		})},
			"sampleNo", { arg view; this.sampleNo = view.value; this.loadSample(view.value); }
		], 
		["TXRangeSlider", "Play Range", ControlSpec(0, 1), "start", "end"], 
		["SynthOptionPopup", "Loop type", arrOptionData, 0, 210], 
		["TXCheckBox", "Reverse", "reverse"], 
		["NextLine"], 
		["EZslider", "Level", ControlSpec(0, 1), "level"], 
		["DividingLine"], 
		["EZslider", "Pre-Delay", ControlSpec(0, 1), "delay"], 
		["EZslider", "Attack*", ControlSpec(0, 1), "attack"], 
		["EZslider", "Decay*", ControlSpec(0, 1), "decay"], 
		["EZslider", "Sustain level", ControlSpec(0, 1), "sustain"], 
		["EZslider", "Sustain time*", ControlSpec(0, 1), "sustainTime"], 
		["EZslider", "Release*", ControlSpec(0, 1), "release"], 
		["EZsliderUnmapped", "* Curve", ControlSpec(-10, 10, step: 1), "curve"], 
		["EZslider", "* Time Scale", ControlSpec(0.001, 20), "timeMultiply"], 
		["DividingLine"], 
		["MIDIChannelSelector"], 
		["TXCheckBox", "Keyboard tracking", "keytrack"], 
		["Transpose"], 
		["DividingLine"], 
	];

	//	use base class initialise 
	this.baseInit(this, argInstName);
	this.midiNoteInit;
	//	make buffers, load the synthdef and create the Group for synths to belong to
	this.makeBuffersAndGroup(arrBufferSpecs);
} // end of method init

extraSaveData { // override default method
	^[sampleNo, sampleFileName, sampleNumChannels, sampleFreq];
}

loadExtraData {arg argData;  // override default method
	sampleNo = argData.at(0);
	sampleFileName = argData.at(1);
	sampleNumChannels = argData.at(2);
	sampleFreq = argData.at(3);
	this.loadSample(sampleNo);
}

loadSample { arg argIndex; // method to load samples into buffer
	var holdBuffer, holdSampleInd;
	Routine.run {
		system.server.sync;
		if (argIndex == 0, {
			// if argIndex is 0, clear the current buffer & filename
			buffers.at(0).zero;
			sampleFileName = "";
			sampleNumChannels = 0;
			sampleFreq = 440;
			// store Freq to synthArgSpecs
			this.setSynthArgSpec("sampleFreq", sampleFreq);
		},{
			// otherwise,  try to load sample.  if it fails, display error message and clear
			holdSampleInd = (argIndex - 1).min(system.sampleBankStereo.size-1);
			holdBuffer = Buffer.read(system.server, system.sampleBankStereo.at(holdSampleInd).at(0), 
				action: { arg argBuffer; 
					{
					//	if file loaded ok
						if (argBuffer.notNil, {
							this.setSynthArgSpec("bufnumSample", argBuffer.bufnum);
							sampleFileName = system.sampleBankStereo.at(holdSampleInd).at(0);
							sampleNumChannels = argBuffer.numChannels;
							sampleFreq = system.sampleBankStereo.at(holdSampleInd).at(1);
							// store Freq to synthArgSpecs
							this.setSynthArgSpec("sampleFreq", sampleFreq);
						},{
							buffers.at(0).zero;
							sampleFileName = "";
							sampleNumChannels = 0;
							sampleFreq = 440;
							// store Freq to synthArgSpecs
							this.setSynthArgSpec("sampleFreq", sampleFreq);
							TXInfoScreen.new("Invalid Sample File" 
							  ++ system.sampleBankStereo.at(holdSampleInd).at(0));
						});
					}.defer;	// defer because gui process
				},
				// pass buffer number
				bufnum: buffers.at(0).bufnum
			);
		});
	}; // end of Routine.run
} // end of method loadSample

}
