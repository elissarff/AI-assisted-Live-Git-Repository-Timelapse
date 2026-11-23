import { createPortal } from "react-dom";
import { useEffect, useRef, useState } from "react";
import type { Commit } from "../../types/git";
import AiExplanation from "./AiExplanation";
export default function AiRange({repoKey,head,commits,from,to}:{repoKey:string;head:string;commits:Commit[];from:string;to:string}) {
  const [open,setOpen]=useState(false);
  const [mode,setMode]=useState("dates");
  const [start,setStart]=useState(from);const [end,setEnd]=useState(to);
  const [first,setFirst]=useState(commits[0]?.sha ?? "");const [last,setLast]=useState(head);
  const dialog=useRef<HTMLDialogElement>(null);
  useEffect(()=>{setStart(from);setEnd(to);},[from,to]);
  useEffect(()=>{setFirst(commits[0]?.sha ?? "");setLast(head);setOpen(false);},[repoKey,head]);
  useEffect(()=>{if(open && dialog.current && !dialog.current.open)dialog.current.showModal();},[open]);
  return <><button className="ai-action" type="button" onClick={()=>setOpen(true)}>✦ Explain range</button>
    {open && createPortal(<dialog className="ai-explanation-popover" ref={dialog} aria-label="Explain range" onCancel={()=>setOpen(false)}>
      <strong>Explain range</strong><button className="ai-close" aria-label="Close range" onClick={()=>setOpen(false)}>×</button>
      <p><select aria-label="Range type" value={mode} onChange={e=>setMode(e.target.value)}><option value="dates">Dates</option><option value="commits">Commits</option></select></p>
      {mode==="dates" ? <div className="ai-range-inputs"><label>From<input type="date" value={start} onChange={e=>setStart(e.target.value)}/></label><label>To<input type="date" value={end} onChange={e=>setEnd(e.target.value)}/></label></div> : <div className="ai-range-inputs">
        <label>From<select value={first} onChange={e=>setFirst(e.target.value)}>{commits.map(c=><option key={c.sha} value={c.sha}>{c.sha.slice(0,8)} · {c.message.slice(0,45)}</option>)}</select></label>
        <label>To<select value={last} onChange={e=>setLast(e.target.value)}>{commits.map(c=><option key={c.sha} value={c.sha}>{c.sha.slice(0,8)} · {c.message.slice(0,45)}</option>)}</select></label>
      </div>}
      <p className="ai-muted">{mode==="dates"?"Inclusive dates · UTC":"Changes after From, through To"}</p>
      <AiExplanation repoKey={repoKey} sha={head} type="PERIOD" from={mode==="dates"?start:undefined} to={mode==="dates"?end:undefined} fromSha={mode==="commits"?first:undefined} toSha={mode==="commits"?last:undefined} label="Explain period" />
    </dialog>,document.body)}
  </>;
}
