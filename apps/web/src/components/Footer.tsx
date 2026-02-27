"use client";

export default function Footer() {
  return (
    <footer className="mt-10 border-t border-white/10 py-6 text-center">
      <div className="text-[12px] text-white/45">
        CryptoLink V2<span className="text-white/25">·</span>{" "}
        Market Intelligence Layer
      </div>

      <div className="mt-1 text-[12px] text-white/35">
        Powered by <a 
        href="http://evilink.dev" 
        target="_blank" 
        rel="noreferrer"
        className="font-semibold text-white/60 hover:text-white/85 underline underline-offset-4"
        >
          evi_Link devs
        </a>
      </div>

      <div className="mt-2 text-[11px] text-white/25">
        Demo build · Data may be delayed · Not financial advice
      </div>
    </footer>
  );
}