package com.twilightcafe.movies.adblock.surrogates;

/**
 * Small stand-ins for the ad scripts pages wait for or check: Google's ads (adsbygoogle), its ad tags
 * (googletag), its analytics and tag manager, Amazon's ads (apstag), and the ad-blocker detectors
 * (FuckAdBlock, BlockAdBlock, bait files). Each answers like the real script with no ads, so the page goes on.
 * The app's own, written for it; plain old JavaScript for the café box's 2018 web engine.
 */
final class Stubs {
    private Stubs() {
    }

    static final String ADSBYGOOGLE =
            "(function(){var w=window,a=w.adsbygoogle;if(!a||typeof a.push!=='function')a=[];"
            + "a.loaded=true;a.push=function(){};w.adsbygoogle=a;"
            + "var e=document.querySelectorAll('ins.adsbygoogle');"
            + "for(var i=0;i<e.length;i++){e[i].setAttribute('data-ad-status','unfilled');e[i].style.display='none';}})();";

    static final String GPT =
            "(function(){var w=window,n=function(){},s=function(){return this;},e=function(){return [];},"
            + "z=function(){return null;},slot={addService:s,clearCategoryExclusions:s,clearTargeting:s,"
            + "defineSizeMapping:s,get:z,getAdUnitPath:function(){return '';},getAttributeKeys:e,getTargeting:e,"
            + "getTargetingKeys:e,getSlotElementId:function(){return '';},set:s,setCategoryExclusion:s,"
            + "setClickUrl:s,setCollapseEmptyDiv:s,setTargeting:s,updateTargetingFromMap:s},"
            + "ads={addEventListener:s,removeEventListener:s,clear:n,clearCategoryExclusions:s,clearTargeting:s,"
            + "collapseEmptyDivs:s,defineOutOfPagePassback:function(){return slot;},"
            + "definePassback:function(){return slot;},disableInitialLoad:n,display:n,enableAsyncRendering:n,"
            + "enableLazyLoad:n,enableSingleRequest:n,enableSyncRendering:n,enableVideoAds:n,get:z,"
            + "getAttributeKeys:e,getTargeting:e,getTargetingKeys:e,getSlots:e,isInitialLoadDisabled:function(){return true;},"
            + "refresh:n,set:s,setCategoryExclusion:s,setCentering:n,setCookieOptions:s,setForceSafeFrame:s,"
            + "setLocation:s,setPrivacySettings:s,setPublisherProvidedId:s,setRequestNonPersonalizedAds:s,"
            + "setSafeFrameConfig:s,setTagForChildDirectedTreatment:s,setTargeting:s,setVideoContent:s,updateCorrelator:n},"
            + "g=w.googletag||{},q=g.cmd||[];g.apiReady=true;g.pubadsReady=true;"
            + "g.cmd={push:function(f){try{f();}catch(x){}return 1;}};"
            + "g.pubads=function(){return ads;};g.companionAds=function(){return ads;};g.content=function(){return ads;};"
            + "g.defineSlot=function(){return slot;};g.defineOutOfPageSlot=function(){return slot;};g.destroySlots=n;"
            + "g.disablePublisherConsole=n;g.display=n;g.enableServices=n;g.getVersion=function(){return '';};"
            + "g.setAdIframeTitle=n;g.sizeMapping=function(){var m={addSize:function(){return m;},build:e};return m;};"
            + "w.googletag=g;for(var i=0;i<q.length;i++){try{q[i]();}catch(x){}}})();";

    static final String ANALYTICS =
            "(function(){var w=window,n=w.GoogleAnalyticsObject||'ga',q=(w[n]&&w[n].q)||[];"
            + "var t={get:function(){},set:function(){},send:function(){}};"
            + "function ga(){var a=arguments,o=a.length?a[a.length-1]:null;"
            + "if(o&&typeof o==='object'&&typeof o.hitCallback==='function'){try{o.hitCallback();}catch(x){}}}"
            + "ga.create=function(){return t;};ga.getByName=function(){return t;};ga.getAll=function(){return [t];};"
            + "ga.remove=function(){};ga.loaded=true;w[n]=ga;"
            + "for(var i=0;i<q.length;i++){try{ga.apply(null,q[i]);}catch(x){}}})();";

    static final String GTM =
            "(function(){var w=window,d=w.dataLayer;function cb(o){if(o&&typeof o.eventCallback==='function'){"
            + "var f=o.eventCallback;setTimeout(function(){try{f();}catch(x){}},1);}}"
            + "if(d&&typeof d.push==='function'){for(var i=0;i<d.length;i++)cb(d[i]);"
            + "var p=d.push;d.push=function(o){cb(o);return p.apply(d,arguments);};}"
            + "w.google_tag_manager=w.google_tag_manager||{};})();";

    static final String NO_DETECTOR =
            "(function(){var w=window;w.canRunAds=true;w.isAdBlockActive=false;w.adblock=false;"
            + "function F(){}F.prototype={check:function(){return false;},clearEvent:function(){return this;},"
            + "emitEvent:function(){return this;},on:function(d,f){if(!d&&typeof f==='function')setTimeout(f,1);return this;},"
            + "onDetected:function(){return this;},onNotDetected:function(f){if(typeof f==='function')setTimeout(f,1);return this;},"
            + "setOption:function(){return this;}};"
            + "w.FuckAdBlock=w.BlockAdBlock=w.SniffAdBlock=F;w.fuckAdBlock=w.blockAdBlock=w.sniffAdBlock=new F();})();";

    static final String APSTAG =
            "(function(){var w=window;w.apstag={_Q:[],fetchBids:function(o,c){if(typeof c==='function')"
            + "setTimeout(function(){try{c([]);}catch(x){}},1);},init:function(){},setDisplayBids:function(){},"
            + "targetingKeys:function(){return [];}};})();";
}
